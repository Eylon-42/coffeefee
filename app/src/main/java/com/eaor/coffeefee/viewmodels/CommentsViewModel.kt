package com.eaor.coffeefee.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.CoffeefeeApplication
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.data.CommentDao
import com.eaor.coffeefee.models.Comment
import com.eaor.coffeefee.repositories.CommentRepository
import com.eaor.coffeefee.repositories.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
class CommentsViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "CommentsViewModel"
    private var postId: String? = null
    // Track current post ID for refreshes
    private var currentPostId: String? = null
    
    // Firebase instances
    protected val auth = FirebaseAuth.getInstance()
    protected val db = FirebaseFirestore.getInstance()
    
    // Keep track of repository LiveData observers
    private var repoCommentsObserver: androidx.lifecycle.Observer<List<Comment>>? = null
    private var repoCommentsLiveData: LiveData<List<Comment>>? = null
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error message
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    // Repository references
    private var commentRepository: CommentRepository? = null
    private var userRepository: UserRepository? = null
    
    // LiveData for comments - this will now come from the repository
    private val _comments = MutableLiveData<List<Comment>>()
    val comments: LiveData<List<Comment>> = _comments
    
    // LiveData for comment count
    private val _commentCount = MutableLiveData<Int>()
    val commentCount: LiveData<Int> = _commentCount
    
    // LiveData for comment clearing status
    private val _clearCommentStatus = MutableLiveData<Boolean>()
    val clearCommentStatus: LiveData<Boolean> = _clearCommentStatus
    
    /**
     * Initialize the ViewModel with the post ID
     */
    fun initialize(postId: String) {
        this.postId = postId
    }
    
    /**
     * Initialize repositories
     */
    fun initializeRepositories(commentRepo: CommentRepository, userRepo: UserRepository) {
        this.commentRepository = commentRepo
        this.userRepository = userRepo
        
        Log.d("CommentsViewModel", "Repositories initialized")
        
        // Now that we have the repositories, load comments
        loadComments()
    }
    
    /**
     * Legacy method to initialize just UserRepository
     */
    fun initializeRepository(repository: UserRepository) {
        this.userRepository = repository
        Log.d("CommentsViewModel", "UserRepository initialized")
    }
    
    /**
     * Load comments for the current post
     */
    private fun loadComments() {
        _isLoading.value = true
        
        postId?.let { id ->
            Log.d("CommentsViewModel", "Loading comments for post $id")
            
            // If we have the comment repository, use it
            if (commentRepository != null) {
                // Get LiveData from repository
                val repoComments = commentRepository!!.getCommentsForPost(id)
                
                // Observe the repository's LiveData and update our own
                viewModelScope.launch {
                    try {
                        // Get initial comments to show loading progress
                        commentRepository?.refreshComments(id)
                        
                        // Check if we got comments through repository
                        val localComments = commentRepository?.getLocalCommentsForPost(id)
                        if (localComments.isNullOrEmpty()) {
                            Log.w("CommentsViewModel", "No comments found through repository, trying legacy approach")
                            // Try legacy approach as fallback
                            legacyLoadComments(id)
                        } else {
                            // Directly update UI with local comments
                            Log.d("CommentsViewModel", "Setting ${localComments.size} comments from local DB")
                            _comments.postValue(localComments)
                            _commentCount.postValue(localComments.size)
                        }
                        
                        // Update loading state
                        _isLoading.value = false
                        _errorMessage.value = null
                    } catch (e: Exception) {
                        Log.e("CommentsViewModel", "Error in initial comments load: ${e.message}")
                        _errorMessage.value = "Error loading comments: ${e.message}"
                        _isLoading.value = false
                        
                        // Try legacy approach as fallback
                        legacyLoadComments(id)
                    }
                }
                
                // Set up observer to keep _comments updated with the repository's LiveData
                viewModelScope.launch(Dispatchers.Main) {
                    // This will keep _comments updated with the latest from the repository
                    repoCommentsObserver = androidx.lifecycle.Observer { newComments -> 
                        if (newComments != null) {
                            Log.d("CommentsViewModel", "Received ${newComments.size} comments from repository LiveData")
                            _comments.value = newComments
                            _commentCount.value = newComments.size
                        
                            // Log all comments for debugging
                            newComments.forEach { comment ->
                                Log.d("CommentsViewModel", "Comment from repo: id=${comment.id}, text='${comment.text}', user=${comment.userName}")
                            }
                        } else {
                            Log.w("CommentsViewModel", "Received null comments from repository")
                        }
                    }
                    
                    // Store reference to LiveData for cleanup
                    repoCommentsLiveData = repoComments
                    repoCommentsLiveData?.observeForever(repoCommentsObserver!!)
                }
            } else {
                // Legacy fallback using Firestore directly
                Log.w("CommentsViewModel", "CommentRepository not initialized, using legacy method")
                legacyLoadComments(id)
            }
        } ?: run {
            Log.e("CommentsViewModel", "Cannot load comments: postId is null")
            _isLoading.value = false
        }
    }
    
    /**
     * Legacy method that loads directly from Firestore
     * This will be removed once CommentRepository is fully implemented
     */
    private fun legacyLoadComments(postId: String) {
        db.collection("Comments")
            .whereEqualTo("postId", postId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _errorMessage.value = "Error loading comments: ${error.message}"
                    Log.e("CommentsViewModel", "Error loading comments: ${error.message}")
                    _isLoading.value = false
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
                        val commentsWithUserData = legacyLoadUserDataForComments(loadedComments)
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
                        _isLoading.value = false
                        _errorMessage.value = null
                    }
                }
            }
    }
    
    /**
     * Legacy method to load user data for comments
     */
    private suspend fun legacyLoadUserDataForComments(comments: List<Comment>): List<Comment> {
        // Legacy implementation moved from original loadUserDataForComments
        Log.d("CommentsViewModel", "Starting to load user data for ${comments.size} comments")
        val userIds = comments.map { it.userId }.distinct()
        Log.d("CommentsViewModel", "Found ${userIds.size} unique users to load")
        
        val resultComments = ArrayList(comments)
        val userDataCache = mutableMapOf<String, Pair<String, String?>>()  // userId -> (name, photoUrl)
        
        if (userRepository != null) {
            for (userId in userIds) {
                try {
                    Log.d("CommentsViewModel", "Using UserRepository to load fresh data for user $userId")
                    val user = userRepository?.getUserData(userId, forceRefresh = false)
                    
                    if (user != null) {
                        Log.d("CommentsViewModel", "Found user in repository: name=${user.name}, photo=${user.profilePhotoUrl}")
                        userDataCache[userId] = Pair(user.name, user.profilePhotoUrl)
                    } else {
                        val refreshedUser = userRepository?.getUserData(userId, forceRefresh = true)
                        if (refreshedUser != null) {
                            userDataCache[userId] = Pair(refreshedUser.name, refreshedUser.profilePhotoUrl)
                            Log.d("CommentsViewModel", "Found user after force refresh: name=${refreshedUser.name}")
                        } else {
                            if (userId == auth.currentUser?.uid) {
                                val name = auth.currentUser?.displayName ?: "User"
                                val photoUrl = auth.currentUser?.photoUrl?.toString()
                                userDataCache[userId] = Pair(name, photoUrl)
                                Log.d("CommentsViewModel", "Using auth data for current user: name=$name")
                            } else {
                                userDataCache[userId] = Pair("User", null)
                                Log.d("CommentsViewModel", "User not found, using default: User")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CommentsViewModel", "Error loading user from repository: ${e.message}")
                    userDataCache[userId] = Pair("User", null)
                }
            }
        } else {
            Log.e("CommentsViewModel", "UserRepository is null - can't load user data properly")
            for (userId in userIds) {
                userDataCache[userId] = Pair("User", null)
            }
        }
        
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
            _errorMessage.value = "You must be logged in to post a comment"
            return
        }
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                postId?.let { id ->
                    // Create new comment
                    val newComment = Comment(
                        id = db.collection("Comments").document().id,
                        postId = id,
                        userId = currentUser.uid,
                        text = commentText.trim(),
                        timestamp = System.currentTimeMillis(),
                        userName = currentUser.displayName ?: "",  // Will be updated by repository
                        userPhotoUrl = currentUser.photoUrl?.toString()  // Will be updated by repository
                    )
                    
                    if (commentRepository != null) {
                        // Use repository to add comment
                        val success = commentRepository!!.addComment(newComment)
                        if (!success) {
                            _errorMessage.value = "Failed to add comment"
                        } else {
                            _errorMessage.value = null
                            // Get actual comment count from Firestore via repository
                            viewModelScope.launch {
                                val firebaseCount = getFirestoreCommentCount(id)
                                if (firebaseCount >= 0) {
                                    updateCommentCount(firebaseCount)
                                } else {
                                    // Fallback: Use the current count + 1
                                    val newCount = (_comments.value?.size ?: 0) + 1
                                    updateCommentCount(newCount)
                                }
                                // Ensure feed and profile will be refreshed
                                com.eaor.coffeefee.GlobalState.triggerRefreshAfterCommentChange()
                            }
                        }
                    } else {
                        // Legacy fallback
                        Log.w("CommentsViewModel", "Using legacy comment add method")
                        legacyAddComment(newComment)
                    }
                } ?: run {
                    Log.e("CommentsViewModel", "Cannot post comment: postId is null")
                    _errorMessage.value = "Cannot post comment: post not specified"
                }
            } catch (e: Exception) {
                Log.e("CommentsViewModel", "Error posting comment: ${e.message}")
                _errorMessage.value = "Error posting comment: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Legacy method to add a comment directly to Firestore
     */
    private suspend fun legacyAddComment(comment: Comment) {
        try {
            val commentData = HashMap<String, Any>()
            commentData["id"] = comment.id
            commentData["postId"] = comment.postId
            commentData["userId"] = comment.userId
            commentData["text"] = comment.text
            commentData["timestamp"] = comment.timestamp
            
            db.collection("Comments")
                .document(comment.id)
                .set(commentData)
                .await()
            
            Log.d("CommentsViewModel", "Comment added successfully with only userId reference")
            _errorMessage.value = null
        } catch (e: Exception) {
            Log.e("CommentsViewModel", "Error in legacy add comment: ${e.message}")
            _errorMessage.value = "Error adding comment: ${e.message}"
        }
    }
    
    /**
     * Update an existing comment
     */
    fun updateComment(commentId: String, newText: String) {
        if (newText.trim().isEmpty()) return
        
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                // Find the comment to update
                val currentComments = _comments.value ?: emptyList()
                val commentToUpdate = currentComments.find { it.id == commentId }
                
                if (commentToUpdate != null) {
                    // Create updated comment
                    val updatedComment = commentToUpdate.copy(text = newText.trim())
                    
                    if (commentRepository != null) {
                        // Use repository to update
                        val success = commentRepository!!.updateComment(updatedComment)
                        if (!success) {
                            _errorMessage.value = "Failed to update comment"
                        } else {
                            _errorMessage.value = null
                            // Ensure feed and profile will be refreshed after editing comment
                            com.eaor.coffeefee.GlobalState.triggerRefreshAfterCommentChange()
                        }
                    } else {
                        // Legacy fallback
                        db.collection("Comments")
                            .document(commentId)
                            .update("text", newText.trim())
                            .await()
                        
                        _errorMessage.value = null
                        // Ensure feed and profile will be refreshed after editing comment
                        com.eaor.coffeefee.GlobalState.triggerRefreshAfterCommentChange()
                    }
                } else {
                    _errorMessage.value = "Comment not found"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error updating comment: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Delete a comment
     */
    fun deleteComment(commentId: String) {
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                postId?.let { id ->
                    if (commentRepository != null) {
                        // Use repository to delete
                        val success = commentRepository!!.deleteComment(commentId, id)
                        if (!success) {
                            _errorMessage.value = "Failed to delete comment"
                        } else {
                            _errorMessage.value = null
                            // Get actual comment count from Firestore via repository
                            viewModelScope.launch {
                                val firebaseCount = getFirestoreCommentCount(id)
                                if (firebaseCount >= 0) {
                                    updateCommentCount(firebaseCount)
                                } else {
                                    // Fallback: Use the current count - 1
                                    val newCount = (_comments.value?.size ?: 1) - 1
                                    updateCommentCount(newCount)
                                }
                                // Ensure feed and profile will be refreshed
                                com.eaor.coffeefee.GlobalState.triggerRefreshAfterCommentChange()
                            }
                        }
                    } else {
                        // Legacy fallback
                        db.collection("Comments")
                            .document(commentId)
                            .delete()
                            .await()
                        
                        // Update comment count
                        updateCommentCount(_comments.value?.size?.minus(1) ?: 0)
                        
                        _errorMessage.value = null
                        
                        // Ensure feed and profile will be refreshed
                        com.eaor.coffeefee.GlobalState.triggerRefreshAfterCommentChange()
                    }
                } ?: run {
                    _errorMessage.value = "Post ID not available for comment deletion"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting comment: ${e.message}", e)
                _errorMessage.value = "Error deleting comment: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Get the actual comment count from Firestore
     */
    private suspend fun getFirestoreCommentCount(postId: String): Int {
        return try {
            val snapshot = db.collection("Comments")
                .whereEqualTo("postId", postId)
                .get()
                .await()
            snapshot.size()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Firestore comment count: ${e.message}")
            -1 // Error value
        }
    }
    
    /**
     * Legacy method to update the comment count for the post
     */
    fun updateCommentCount(count: Int) {
        // Update local LiveData count
        _commentCount.value = count
        
        postId?.let { id ->
            viewModelScope.launch {
                try {
                    db.collection("Posts")
                        .document(id)
                        .set(mapOf("commentCount" to count), com.google.firebase.firestore.SetOptions.merge())
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
        
        postId?.let { id ->
            if (commentRepository != null) {
                // Use repository to refresh
                viewModelScope.launch {
                    try {
                        commentRepository!!.refreshComments(id)
                    } catch (e: Exception) {
                        Log.e("CommentsViewModel", "Error refreshing comments: ${e.message}")
                    }
                }
            } else {
                // Legacy refresh
                val currentComments = _comments.value ?: emptyList()
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
                            val updatedComments = legacyLoadUserDataForComments(currentComments)
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
    }
    
    /**
     * Clean up Firestore listeners when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        // Clean up any listeners
        commentRepository?.removeListeners()
        
        // Clean up our LiveData observers
        if (repoCommentsObserver != null && repoCommentsLiveData != null) {
            repoCommentsLiveData?.removeObserver(repoCommentsObserver!!)
            Log.d(TAG, "Cleaned up repository LiveData observer")
        }
    }
    
    /**
     * Clear all comments from cache
     */
    fun clearAllComments() {
        viewModelScope.launch {
            try {
                commentRepository?.clearAllComments()
                _comments.value = emptyList()
                _clearCommentStatus.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing all comments: ${e.message}")
                // Direct database operation as fallback
                try {
                    val commentsDao = getCommentsDao()
                    commentsDao?.deleteAllComments()
                    _comments.value = emptyList()
                    _clearCommentStatus.value = true
                } catch (innerEx: Exception) {
                    Log.e(TAG, "Fallback error when clearing all comments: ${innerEx.message}")
                    _clearCommentStatus.value = false
                }
            }
        }
    }
    
    /**
     * Clear comments for a specific post
     */
    fun clearCommentsForPost(postId: String) {
        viewModelScope.launch {
            try {
                commentRepository?.clearCommentsForPost(postId)
                // Refresh comments for the current post
                currentPostId?.let {
                    fetchCommentsForPost(it) 
                }
                _clearCommentStatus.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing comments for post: ${e.message}")
                // Direct database operation as fallback
                try {
                    val commentsDao = getCommentsDao()
                    commentsDao?.deleteCommentsForPost(postId)
                    // Refresh comments for the current post
                    currentPostId?.let {
                        fetchCommentsForPost(it)
                    }
                    _clearCommentStatus.value = true
                } catch (innerEx: Exception) {
                    Log.e(TAG, "Fallback error when clearing comments for post: ${innerEx.message}")
                    _clearCommentStatus.value = false
                }
            }
        }
    }
    
    /**
     * Fetch comments for a post - can be called to refresh comments
     */
    fun fetchCommentsForPost(postId: String) {
        this.currentPostId = postId
        this.postId = postId
        loadComments()
    }

    private fun getCommentsDao(): CommentDao? {
        return try {
            (getApplication() as CoffeefeeApplication).database.commentDao()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting CommentDao: ${e.message}")
            null
        }
    }
} 