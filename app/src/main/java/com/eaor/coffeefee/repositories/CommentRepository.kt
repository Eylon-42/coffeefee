package com.eaor.coffeefee.repositories

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.eaor.coffeefee.data.CommentDao
import com.eaor.coffeefee.data.CommentEntity
import com.eaor.coffeefee.models.Comment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for managing comment data, following the repository pattern.
 * Provides a clean API for data access to the rest of the application.
 */
class CommentRepository(
    private val commentDao: CommentDao,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository
) {
    private val TAG = "CommentRepository"
    
    // Stores active listeners to prevent memory leaks
    private val firestoreListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
    
    /**
     * Get comments for a post with user data
     * Returns LiveData that combines Room database with Firestore updates
     */
    fun getCommentsForPost(postId: String): LiveData<List<Comment>> {
        val result = MediatorLiveData<List<Comment>>()
        
        // Start with data from Room cache
        val dbSource = commentDao.getCommentsForPostLive(postId)
        
        result.addSource(dbSource) { commentEntities ->
            Log.d(TAG, "Room provided ${commentEntities.size} comments for post $postId")
            
            // Convert entities to domain models
            CoroutineScope(Dispatchers.IO).launch {
                val commentsWithUserData = loadUserDataForComments(commentEntities)
                withContext(Dispatchers.Main) {
                    result.value = commentsWithUserData
                }
            }
        }
        
        // Set up Firestore listener to keep data fresh
        setupFirestoreListener(postId, result)
        
        return result
    }
    
    /**
     * Sets up a Firestore listener for real-time updates
     */
    private fun setupFirestoreListener(postId: String, resultLiveData: MediatorLiveData<List<Comment>>) {
        // Remove any existing listener for this post
        firestoreListeners[postId]?.remove()
        
        // Create a new listener
        val listener = firestore.collection("Comments")
            .whereEqualTo("postId", postId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for comment updates: ${error.message}")
                    return@addSnapshotListener
                }
                
                // Process the snapshot and update the database
                snapshot?.let { querySnapshot ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Log.d(TAG, "Got snapshot with ${querySnapshot.documents.size} comments")
                            val comments = querySnapshot.documents.mapNotNull { doc ->
                                try {
                                    Comment(
                                        id = doc.id,
                                        postId = doc.getString("postId") ?: return@mapNotNull null,
                                        userId = doc.getString("userId") ?: return@mapNotNull null,
                                        text = doc.getString("text") ?: return@mapNotNull null,
                                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                        userName = doc.getString("userName") ?: "",
                                        userPhotoUrl = doc.getString("userPhotoUrl")
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing comment doc: ${e.message}")
                                    null
                                }
                            }
                            
                            if (comments.isEmpty()) {
                                Log.d(TAG, "No comments received from Firestore")
                                return@launch
                            }
                            
                            // Save to Room database
                            val entities = comments.map { CommentEntity.fromComment(it) }
                            commentDao.insertComments(entities)
                            
                            Log.d(TAG, "Updated Room with ${entities.size} comments from Firestore")
                            
                            // We don't need to manually update the LiveData
                            // Room will trigger the observer we set up earlier
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing comment snapshot: ${e.message}")
                        }
                    }
                }
            }
        
        // Store the listener for cleanup
        firestoreListeners[postId] = listener
    }
    
    /**
     * Add user data to comments
     */
    private suspend fun loadUserDataForComments(commentEntities: List<CommentEntity>): List<Comment> {
        val result = ArrayList<Comment>()
        val userDataCache = mutableMapOf<String, Pair<String, String?>>()
        
        Log.d(TAG, "Loading user data for ${commentEntities.size} comments")
        
        if (commentEntities.isEmpty()) {
            Log.d(TAG, "No comments to load user data for")
            return emptyList()
        }
        
        for (entity in commentEntities) {
            var userName = ""
            var userPhotoUrl: String? = null
            
            // Check cache first
            if (userDataCache.containsKey(entity.userId)) {
                val cached = userDataCache[entity.userId]
                userName = cached?.first ?: ""
                userPhotoUrl = cached?.second
                Log.d(TAG, "Using cached user data for ${entity.userId}: name=$userName")
            } else {
                // Get from repository
                try {
                    val user = userRepository.getUserData(entity.userId)
                    if (user != null) {
                        userName = user.name
                        userPhotoUrl = user.profilePhotoUrl
                        userDataCache[entity.userId] = Pair(userName, userPhotoUrl)
                        Log.d(TAG, "Got user data for ${entity.userId}: name=$userName")
                    } else {
                        Log.d(TAG, "No user data found for ${entity.userId}, using default")
                        userName = "User"
                        userDataCache[entity.userId] = Pair(userName, null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting user data for comment: ${e.message}")
                    userName = "User"
                    userDataCache[entity.userId] = Pair(userName, null)
                }
            }
            
            val comment = entity.toComment(userName, userPhotoUrl)
            result.add(comment)
            Log.d(TAG, "Added comment to result: id=${comment.id}, text='${comment.text.take(20)}...', user=$userName")
        }
        
        Log.d(TAG, "Returning ${result.size} comments with user data")
        return result
    }
    
    /**
     * Add a comment
     */
    suspend fun addComment(comment: Comment): Boolean {
        return try {
            // First add to Firestore
            val commentData = comment.toMap()
            
            firestore.collection("Comments")
                .document(comment.id)
                .set(commentData)
                .await()
            
            // Then update local cache
            val entity = CommentEntity.fromComment(comment)
            commentDao.insertComment(entity)
            
            // Force an immediate refresh for UI update
            val localComments = getLocalCommentsForPost(comment.postId)
            
            // Update comment count in post document
            val newCount = updateCommentCount(comment.postId)
            
            Log.d(TAG, "Comment added successfully: ${comment.id}")
            Log.d(TAG, "Local database now has ${localComments.size} comments for post ${comment.postId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding comment: ${e.message}")
            false
        }
    }
    
    /**
     * Update an existing comment
     */
    suspend fun updateComment(comment: Comment): Boolean {
        return try {
            // Update in Firestore
            firestore.collection("Comments")
                .document(comment.id)
                .update("text", comment.text)
                .await()
            
            // Update in Room
            val entity = CommentEntity.fromComment(comment)
            commentDao.updateComment(entity)
            
            Log.d(TAG, "Comment updated successfully: ${comment.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating comment: ${e.message}")
            false
        }
    }
    
    /**
     * Delete a comment
     */
    suspend fun deleteComment(commentId: String, postId: String): Boolean {
        return try {
            Log.d(TAG, "Starting to delete comment $commentId for post $postId")
            
            // First get the current count before deleting anything
            val currentCount = try {
                val snapshot = firestore.collection("Comments")
                    .whereEqualTo("postId", postId)
                    .get()
                    .await()
                snapshot.size()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting current comment count: ${e.message}")
                -1 // Error value
            }
            
            Log.d(TAG, "Current comment count before deletion: $currentCount")
            
            // Delete from Firestore
            firestore.collection("Comments")
                .document(commentId)
                .delete()
                .await()
            
            // Delete from Room
            commentDao.deleteComment(commentId)
            
            // Verify the comment was deleted by getting new count
            val newCount = try {
                val snapshot = firestore.collection("Comments")
                    .whereEqualTo("postId", postId)
                    .get()
                    .await()
                snapshot.size()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting new comment count: ${e.message}")
                if (currentCount > 0) currentCount - 1 else 0 // Fallback to estimate
            }
            
            Log.d(TAG, "New comment count after deletion: $newCount")
            
            // Update the post document with accurate count
            try {
                firestore.collection("Posts")
                    .document(postId)
                    .set(mapOf("commentCount" to newCount), SetOptions.merge())
                    .await()
                Log.d(TAG, "Successfully updated comment count in Posts collection: $newCount")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating comment count in Posts collection: ${e.message}")
                // Continue execution even if this part fails
            }
            
            Log.d(TAG, "Comment deleted successfully: $commentId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting comment: ${e.message}", e)
            false
        }
    }
    
    /**
     * Update comment count for a post
     */
    private suspend fun updateCommentCount(postId: String): Int {
        try {
            // Count comments in Firestore
            val snapshot = firestore.collection("Comments")
                .whereEqualTo("postId", postId)
                .get()
                .await()
            
            val count = snapshot.size()
            
            // Log for debugging
            Log.d(TAG, "Counted $count comments for post $postId in Firestore")
            
            // Update post document with accurate count
            firestore.collection("Posts")
                .document(postId)
                .set(mapOf("commentCount" to count), SetOptions.merge())
                .await()
            
            Log.d(TAG, "Successfully updated comment count in Firestore for post $postId: $count")
            return count
        } catch (e: Exception) {
            Log.e(TAG, "Error updating comment count: ${e.message}")
            return -1
        }
    }
    
    /**
     * Clean up Firestore listeners to prevent memory leaks
     */
    fun removeListeners() {
        for ((postId, listener) in firestoreListeners) {
            Log.d(TAG, "Removing listener for post $postId")
            listener.remove()
        }
        firestoreListeners.clear()
    }
    
    /**
     * Force a refresh of comments for a post
     */
    suspend fun refreshComments(postId: String) {
        try {
            Log.d(TAG, "Forcing refresh of comments for post $postId")
            
            // Get comments from Firestore
            val commentDocs = firestore.collection("Comments")
                .whereEqualTo("postId", postId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val comments = commentDocs.documents.mapNotNull { doc ->
                try {
                    Comment(
                        id = doc.id,
                        postId = doc.getString("postId") ?: return@mapNotNull null,
                        userId = doc.getString("userId") ?: return@mapNotNull null,
                        text = doc.getString("text") ?: return@mapNotNull null,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        userName = "", // Will be filled by the adapter
                        userPhotoUrl = null // Will be filled by the adapter
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing comment during refresh: ${e.message}")
                    null
                }
            }
            
            Log.d(TAG, "Retrieved ${comments.size} comments from Firestore during refresh")
            
            // Save to Room database
            val entities = comments.map { CommentEntity.fromComment(it) }
            if (entities.isNotEmpty()) {
                // First clear existing comments
                commentDao.deleteCommentsForPost(postId)
                // Then insert the new ones
                commentDao.insertComments(entities)
                Log.d(TAG, "Updated Room with ${entities.size} fresh comments")
            } else {
                Log.d(TAG, "No comments to refresh for post $postId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing comments: ${e.message}")
            throw e
        }
    }
    
    /**
     * Clear cached comments for a specific post
     */
    suspend fun clearCommentsForPost(postId: String) {
        try {
            // Clear from Room cache
            commentDao.deleteCommentsForPost(postId)
            Log.d(TAG, "Cleared cached comments for post $postId")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cached comments: ${e.message}")
        }
    }
    
    /**
     * Clear all cached comments
     */
    suspend fun clearAllComments() {
        try {
            // Clear from Room cache
            commentDao.deleteAllComments()
            Log.d(TAG, "Cleared all cached comments")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all cached comments: ${e.message}")
        }
    }
    
    /**
     * Get comments from the local database only (for direct access)
     */
    suspend fun getLocalCommentsForPost(postId: String): List<Comment> {
        return try {
            Log.d(TAG, "Getting local comments for post $postId")
            val commentEntities = commentDao.getCommentsForPost(postId)
            Log.d(TAG, "Found ${commentEntities.size} local comments")
            
            // Convert entities to domain models with user data
            loadUserDataForComments(commentEntities)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local comments: ${e.message}")
            emptyList()
        }
    }
} 