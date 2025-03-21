package com.eaor.coffeefee.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.models.Comment
import com.eaor.coffeefee.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CommentsViewModel follows the MVVM architecture pattern.
 * It uses LiveData for reactive UI updates and communicates with the Repository layer
 * to fetch and update data. The UI observes these LiveData objects and automatically
 * updates when the data changes.
 */
class CommentsViewModel : BaseViewModel() {
    private var postId: String? = null
    
    // LiveData for comments
    private val _comments = MutableLiveData<List<Comment>>()
    val comments: LiveData<List<Comment>> = _comments
    
    // LiveData for comment count
    private val _commentCount = MutableLiveData<Int>()
    val commentCount: LiveData<Int> = _commentCount
    
    // UserRepository for consistent user data loading
    private var userRepository: UserRepository? = null
    
    /**
     * Initialize the ViewModel with the post ID
     */
    fun initialize(postId: String) {
        this.postId = postId
        loadComments()
    }
    
    /**
     * Initialize UserRepository
     */
    fun initializeRepository(repository: UserRepository) {
        this.userRepository = repository
        Log.d("CommentsViewModel", "UserRepository initialized")
    }
    
    /**
     * Load comments for the current post
     */
    private fun loadComments() {
        setLoading(true)
        
        postId?.let { id ->
            Log.d("CommentsViewModel", "Loading comments for post $id")
            db.collection("Comments")
                .whereEqualTo("postId", id)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        setError("Error loading comments: ${error.message}")
                        Log.e("CommentsViewModel", "Error loading comments: ${error.message}")
                        setLoading(false)
                        return@addSnapshotListener
                    }

                    val loadedComments = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            Log.d("CommentsViewModel", "Processing comment document: ${doc.id}")
                            
                            // Get userName and userPhotoUrl directly from the comment document if available
                            val userName = doc.getString("userName")
                            val userPhotoUrl = doc.getString("userPhotoUrl")
                            val userId = doc.getString("userId")
                            
                            Log.d("CommentsViewModel", "Comment data from doc: userId=$userId, userName=$userName, photoUrl=$userPhotoUrl")
                            
                            if (userId == null) {
                                Log.e("CommentsViewModel", "Comment missing userId, skipping")
                                return@mapNotNull null
                            }
                            
                            Comment(
                                id = doc.id,
                                postId = doc.getString("postId") ?: return@mapNotNull null,
                                userId = userId,
                                text = doc.getString("text") ?: return@mapNotNull null,
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                userName = userName ?: "User",
                                userPhotoUrl = userPhotoUrl
                            )
                        } catch (e: Exception) {
                            Log.e("CommentsViewModel", "Error parsing comment: ${e.message}")
                            null
                        }
                    } ?: emptyList()

                    Log.d("CommentsViewModel", "Loaded ${loadedComments.size} comments, now fetching user data")
                    
                    // Load user data for all comments
                    viewModelScope.launch {
                        try {
                            val commentsWithUserData = loadUserDataForComments(loadedComments)
                            Log.d("CommentsViewModel", "Finished loading user data for comments")
                            
                            _comments.postValue(commentsWithUserData)
                            _commentCount.postValue(commentsWithUserData.size)
                            updateCommentCount(commentsWithUserData.size)
                            
                            // Print all comments with their user data for debugging
                            commentsWithUserData.forEach { comment ->
                                Log.d("CommentsViewModel", "Comment with user data - userId: ${comment.userId}, userName: ${comment.userName}, photoUrl: ${comment.userPhotoUrl}")
                            }
                        } catch (e: Exception) {
                            Log.e("CommentsViewModel", "Error loading user data: ${e.message}", e)
                            _comments.postValue(loadedComments)
                            _commentCount.postValue(loadedComments.size)
                            updateCommentCount(loadedComments.size)
                        } finally {
                            setLoading(false)
                            clearError()
                        }
                    }
                }
        } ?: run {
            Log.e("CommentsViewModel", "Cannot load comments: postId is null")
            setLoading(false)
        }
    }
    
    /**
     * Load user data for all comments using UserRepository if available
     */
    private suspend fun loadUserDataForComments(comments: List<Comment>): List<Comment> {
        Log.d("CommentsViewModel", "Starting to load user data for ${comments.size} comments")
        val userIds = comments.map { it.userId }.distinct()
        Log.d("CommentsViewModel", "Found ${userIds.size} unique users to load")
        
        val resultComments = ArrayList(comments)
        val userDataCache = mutableMapOf<String, Pair<String, String?>>()  // userId -> (name, photoUrl)
        
        // Always fetch fresh user data from repository for all users
        // This ensures profile changes are reflected in comments
        if (userRepository != null) {
            for (userId in userIds) {
                try {
                    Log.d("CommentsViewModel", "Using UserRepository to load fresh data for user $userId")
                    // Force a refresh from Firestore by passing forceRefresh=true
                    // This helps when a user has updated their profile
                    val user = userRepository?.getUserData(userId, forceRefresh = false)
                    
                    if (user != null) {
                        // User data found in repository
                        Log.d("CommentsViewModel", "Found user in repository: name=${user.name}, photo=${user.profilePhotoUrl}")
                        userDataCache[userId] = Pair(user.name, user.profilePhotoUrl)
                    } else {
                        // If not in Room, try a force refresh
                        val refreshedUser = userRepository?.getUserData(userId, forceRefresh = true)
                        if (refreshedUser != null) {
                            userDataCache[userId] = Pair(refreshedUser.name, refreshedUser.profilePhotoUrl)
                            Log.d("CommentsViewModel", "Found user after force refresh: name=${refreshedUser.name}")
                        } else {
                            // Fallback for users not found: use Firebase Auth if this is the current user
                            if (userId == auth.currentUser?.uid) {
                                val name = auth.currentUser?.displayName ?: "User"
                                val photoUrl = auth.currentUser?.photoUrl?.toString()
                                userDataCache[userId] = Pair(name, photoUrl)
                                Log.d("CommentsViewModel", "Using auth data for current user: name=$name")
                            } else {
                                // For other users, use a default value
                                userDataCache[userId] = Pair("User", null)
                                Log.d("CommentsViewModel", "User not found, using default: User")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CommentsViewModel", "Error loading user from repository: ${e.message}")
                    // Use defaults on error
                    userDataCache[userId] = Pair("User", null)
                }
            }
        } else {
            Log.e("CommentsViewModel", "UserRepository is null - can't load user data properly")
            // Set defaults for all users if repository not available
            for (userId in userIds) {
                userDataCache[userId] = Pair("User", null)
            }
        }
        
        // Update comments with user data from cache
        for (i in resultComments.indices) {
            val userId = resultComments[i].userId
            val userData = userDataCache[userId]
            
            if (userData != null) {
                val (name, photoUrl) = userData
                resultComments[i] = resultComments[i].copy(
                    userName = name.ifBlank { "User" },
                    userPhotoUrl = photoUrl
                )
            }
        }
        
        return resultComments
    }
    
    /**
     * Add a new comment
     */
    fun addComment(commentText: String) {
        if (commentText.trim().isEmpty()) return
        
        val currentUser = auth.currentUser ?: run {
            setError("You must be logged in to post a comment")
            return
        }
        
        // Post comment with just user ID, without storing redundant user data
        viewModelScope.launch {
            try {
                setLoading(true)
                
                // Create and post the comment
                postId?.let { id ->
                    // Create comment with just the user ID reference
                    val newComment = Comment(
                        id = db.collection("Comments").document().id,
                        postId = id,
                        userId = currentUser.uid,
                        text = commentText.trim(),
                        timestamp = System.currentTimeMillis(),
                        // Don't set userName or userPhotoUrl as we'll fetch them dynamically
                        userName = "",  // This will be populated dynamically when comments are loaded
                        userPhotoUrl = null  // This will be populated dynamically when comments are loaded
                    )
                    
                    // Add comment to Firestore without redundant user data
                    val commentData = HashMap<String, Any>()
                    commentData["id"] = newComment.id
                    commentData["postId"] = newComment.postId
                    commentData["userId"] = newComment.userId
                    commentData["text"] = newComment.text
                    commentData["timestamp"] = newComment.timestamp
                    
                    db.collection("Comments")
                        .document(newComment.id)
                        .set(commentData)
                        .await()
                    
                    Log.d("CommentsViewModel", "Comment added successfully with only userId reference")
                    clearError()
                    
                    // Reload comments is handled by the snapshot listener
                } ?: run {
                    Log.e("CommentsViewModel", "Cannot post comment: postId is null")
                    setError("Cannot post comment: post not specified")
                }
            } catch (e: Exception) {
                Log.e("CommentsViewModel", "Error posting comment: ${e.message}")
                setError("Error posting comment: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Update an existing comment
     */
    fun updateComment(commentId: String, newText: String) {
        if (newText.trim().isEmpty()) return
        
        setLoading(true)
        
        viewModelScope.launch {
            try {
                db.collection("Comments")
                    .document(commentId)
                    .update("text", newText.trim())
                    .await()
                
                clearError()
            } catch (e: Exception) {
                setError("Error updating comment: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Delete a comment
     */
    fun deleteComment(commentId: String) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                db.collection("Comments")
                    .document(commentId)
                    .delete()
                    .await()
                
                clearError()
            } catch (e: Exception) {
                setError("Error deleting comment: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Update the comment count for the post
     */
    private fun updateCommentCount(count: Int) {
        postId?.let { id ->
            viewModelScope.launch {
                try {
                    db.collection("Posts")
                        .document(id)
                        .set(mapOf("commentCount" to count), SetOptions.merge())
                        .await()
                } catch (e: Exception) {
                    Log.e("CommentsViewModel", "Error updating comment count: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Force a refresh of all comments to update user data
     * This is called when a user profile is updated
     */
    fun refreshComments() {
        Log.d("CommentsViewModel", "Refreshing comments to get updated user data")
        
        // Get current comments
        val currentComments = _comments.value ?: emptyList()
        
        // If we have comments, reload user data for them with a forced refresh
        if (currentComments.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    // First get unique user IDs from comments
                    val userIds = currentComments.map { it.userId }.distinct()
                    
                    // Force refresh data for these users from Firestore to Room
                    for (userId in userIds) {
                        userRepository?.getUserData(userId, forceRefresh = true)
                    }
                    
                    // Then load comments with updated user data
                    val updatedComments = loadUserDataForComments(currentComments)
                    _comments.postValue(updatedComments)
                    
                    Log.d("CommentsViewModel", "Comments refreshed with updated user data")
                } catch (e: Exception) {
                    Log.e("CommentsViewModel", "Error refreshing comments: ${e.message}")
                }
            }
        } else {
            // If no comments yet, just reload all comments
            loadComments()
        }
    }
} 