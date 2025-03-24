package com.eaor.coffeefee.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.models.FeedItem
import com.eaor.coffeefee.repositories.FeedRepository
import com.eaor.coffeefee.repositories.UserRepository
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import android.util.Log
import kotlinx.coroutines.Dispatchers
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.GlobalState
import com.google.firebase.Timestamp
import android.content.Intent
import com.eaor.coffeefee.adapters.FeedAdapter
import kotlinx.coroutines.withContext

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = FirebaseStorage.getInstance()
    
    private val _userData = MutableLiveData<Map<String, Any>>()
    val userData: LiveData<Map<String, Any>> = _userData
    
    private val _userPosts = MutableLiveData<List<FeedItem>>()
    val userPosts: LiveData<List<FeedItem>> = _userPosts
    
    private val _updateSuccess = MutableLiveData<Boolean>()
    val updateSuccess: LiveData<Boolean> = _updateSuccess
    
    // Add payload for adapter updates
    private val _payload = MutableLiveData<Pair<Int, String>>()
    val payload: LiveData<Pair<Int, String>> = _payload
    
    private lateinit var feedRepository: FeedRepository
    private lateinit var userRepository: UserRepository
    
    // Firebase instances
    protected val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    protected val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error message
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    companion object {
        private const val TAG = "ProfileViewModel"
    }
    
    protected fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    protected fun setError(message: String?) {
        _errorMessage.value = message
    }
    
    protected fun clearError() {
        _errorMessage.value = null
    }
    
    // Set the repository (will be called from fragment)
    fun setRepository(repository: FeedRepository) {
        feedRepository = repository
    }
    
    // Set the user repository (will be called from fragment)
    fun setUserRepository(repository: UserRepository) {
        userRepository = repository
    }

    fun loadUserData(userId: String? = null) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    setError("User not authenticated")
                    setLoading(false)
                    return@launch
                }
                
                // Focus only on the Users collection with capital U
                val userDoc = db.collection("Users").document(currentUser.uid).get().await()
                if (userDoc.exists()) {
                    _userData.value = userDoc.data ?: mapOf()
                } else {
                    // If not found, use local auth data as fallback
                    _userData.value = mapOf(
                        "userId" to currentUser.uid,
                        "name" to (currentUser.displayName ?: ""),
                        "email" to (currentUser.email ?: ""),
                        "profilePhotoUrl" to (currentUser.photoUrl?.toString() ?: "")
                    )
                }
                setLoading(false)
            } catch (e: Exception) {
                setError("Error loading user data: ${e.message}")
                setLoading(false)
            }
        }
    }
    
    fun loadUserPosts(userId: String? = null, forceRefresh: Boolean = false) {
        // Check if we already have posts data and don't need to refresh
        if (!forceRefresh && userId == _userPosts.value?.firstOrNull()?.userId && _userPosts.value?.isNotEmpty() == true) {
            Log.d("ProfileViewModel", "Using existing posts data for user $userId (${_userPosts.value?.size} posts)")
            return
        }
        
        setLoading(true)
        
        Log.d("ProfileViewModel", "Loading user posts, forceRefresh=$forceRefresh")
        
        viewModelScope.launch {
            try {
                val targetUserId = userId ?: auth.currentUser?.uid
                if (targetUserId == null) {
                    setError("User not authenticated")
                    setLoading(false)
                    Log.e("ProfileViewModel", "User not authenticated")
                    return@launch
                }
                
                Log.d("ProfileViewModel", "Looking for posts with userId: $targetUserId")
                
                // First try to get posts from local database
                var posts = feedRepository.getFeedItemsByUserId(targetUserId)
                
                // If no local results or force refresh requested, fetch from Firestore
                if (posts.isEmpty() || forceRefresh) {
                    Log.d("ProfileViewModel", "Fetching posts from Firestore, forceRefresh=$forceRefresh, local posts count=${posts.size}")
                    posts = feedRepository.loadUserPosts(targetUserId)
                } else {
                    Log.d("ProfileViewModel", "Using posts from Room database, count=${posts.size}")
                }
                
                Log.d("ProfileViewModel", "Final post count: ${posts.size}")
                _userPosts.value = posts
                setLoading(false)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading user posts: ${e.message}", e)
                setError("Error loading user posts: ${e.message}")
                setLoading(false)
            }
        }
    }
    
    fun updateProfile(name: String, imageUri: Uri?) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                if (user == null) {
                    setError("User not authenticated")
                    setLoading(false)
                    return@launch
                }
                
                Log.d("ProfileViewModel", "Starting profile update for user ${user.uid}")
                
                // Upload new profile image if provided
                val profilePhotoUrl = if (imageUri != null) {
                    val photoRef = storage.reference.child("profiles/${user.uid}/${UUID.randomUUID()}.jpg")
                    photoRef.putFile(imageUri).await()
                    photoRef.downloadUrl.await().toString()
                } else {
                    user.photoUrl?.toString() ?: ""
                }
                
                // Update user profile in Auth
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .setPhotoUri(if (profilePhotoUrl.isNotEmpty()) Uri.parse(profilePhotoUrl) else null)
                    .build()
                
                user.updateProfile(profileUpdates).await()
                Log.d("ProfileViewModel", "Updated Firebase Auth profile")
                
                // Invalidate Picasso cache for the user photo
                if (profilePhotoUrl.isNotEmpty()) {
                    try {
                        // Using the ImageLoader utility instead of directly invalidating the cache
                        // This approach allows the ImageLoader to manage caching strategy
                        Log.d("ProfileViewModel", "Profile photo URL updated: $profilePhotoUrl")
                    } catch (e: Exception) {
                        Log.e("ProfileViewModel", "Error with profile photo URL: ${e.message}")
                    }
                }
                
                // Update user data in Firestore - use Users collection with capital U
                val userData = mapOf(
                    "name" to name,
                    "email" to (user.email ?: ""),
                    "profilePhotoUrl" to profilePhotoUrl  // Use consistent field name
                )
                
                db.collection("Users").document(user.uid)
                    .set(userData, com.google.firebase.firestore.SetOptions.merge())
                    .await()
                
                Log.d("ProfileViewModel", "Updated user data in Firestore")
                
                // Clear all user data caches to ensure fresh data
                userRepository.clearUserCache(user.uid)
                Log.d("ProfileViewModel", "Cleared user cache")
                
                // Use our improved method to update all posts with new user data
                try {
                    feedRepository.refreshUserDataInAllPosts(userId = user.uid, forceRefresh = true)
                    Log.d("ProfileViewModel", "Refreshed user data in all posts")
                } catch (e: Exception) {
                    // Log error but don't fail the entire operation
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.w("ProfileViewModel", "Post update job was cancelled, continuing with profile update")
                    } else {
                        Log.e("ProfileViewModel", "Error updating user data in posts: ${e.message}")
                    }
                    // Don't rethrow, continue with the rest of the process
                }
                
                // Set a success flag that can be observed
                _updateSuccess.value = true
                
                // Check if user-visible data that affects posts has changed
                val userDataChanged = user.displayName != name || 
                                     (profilePhotoUrl.isNotEmpty() && user.photoUrl?.toString() != profilePhotoUrl)
                
                // Update global flags to trigger UI refresh across the app
                GlobalState.triggerRefreshAfterProfileChange(dataChanged = true)
                
                // Only refresh feed if user data actually changed
                if (userDataChanged) {
                    Log.d("ProfileViewModel", "User profile data that affects posts has changed, setting feed refresh flag")
                    GlobalState.shouldRefreshFeed = true
                } else {
                    Log.d("ProfileViewModel", "No visible user data changes, skipping feed refresh flag")
                }
                
                // Send broadcast to notify app components of the change
                val context = getApplication<android.app.Application>().applicationContext
                val intent = android.content.Intent("com.eaor.coffeefee.PROFILE_UPDATED").apply {
                    putExtra("userId", user.uid)
                    putExtra("userName", name)
                    putExtra("userPhotoUrl", profilePhotoUrl)
                    putExtra("timestamp", System.currentTimeMillis())
                }
                context.sendBroadcast(intent)
                Log.d("ProfileViewModel", "Sent profile update broadcast")
                
                // Reload user data in view model
                loadUserData()
                
                // Reload user posts to reflect the updated user data
                refreshUserPosts(user.uid)
                
                setLoading(false)
                Log.d("ProfileViewModel", "Profile update completed successfully")
            } catch (e: Exception) {
                setError("Error updating profile: ${e.message}")
                _updateSuccess.value = false
                setLoading(false)
                Log.e("ProfileViewModel", "Error updating profile: ${e.message}", e)
            }
        }
    }
    
    fun deletePost(postId: String) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                if (user == null) {
                    setError("User not authenticated")
                    setLoading(false)
                    return@launch
                }
                
                // First, get the post to check for image URL
                val postSnapshot = db.collection("Posts").document(postId).get().await()
                val photoUrl = postSnapshot.getString("photoUrl")
                
                // Delete from Firestore first
                db.collection("Posts").document(postId).delete().await()
                
                // Delete the image from Firebase Storage if exists
                if (!photoUrl.isNullOrEmpty()) {
                    try {
                        // Extract the filename from the URL
                        // Firebase Storage URLs look like: https://firebasestorage.googleapis.com/v0/b/bucket/o/Posts%2Ffilename.jpg?token...
                        val decodedUrl = Uri.decode(photoUrl)
                        val filenameWithPath = decodedUrl.substringAfter("/o/").substringBefore("?")
                        Log.d("ProfileViewModel", "Extracted image path: $filenameWithPath")
                        
                        // Use the full path from the URL instead of assuming Posts/$postId.jpg
                        val storage = FirebaseStorage.getInstance()
                        val storageRef = storage.reference.child(filenameWithPath)
                        
                        storageRef.delete().await()
                        Log.d("ProfileViewModel", "Successfully deleted image from storage: $filenameWithPath")
                    } catch (e: Exception) {
                        Log.e("ProfileViewModel", "Error deleting image from storage: ${e.message}")
                        // Continue with post deletion even if image deletion fails
                    }
                }
                
                // Then delete from local Room database to keep them in sync
                feedRepository.deleteFeedItem(postId)
                
                // Get current posts and filter out the deleted one
                val currentPosts = _userPosts.value?.toMutableList() ?: mutableListOf()
                val updatedPosts = currentPosts.filter { it.id != postId }
                _userPosts.value = updatedPosts
                
                // Broadcast the change to trigger refresh in other fragments
                broadcastPostChange("com.eaor.coffeefee.POST_DELETED", postId)
                
                // Set global refresh flags
                GlobalState.shouldRefreshFeed = true
                GlobalState.postsWereChanged = true // Mark that posts were actually changed
                
                setLoading(false)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error deleting post: ${e.message}")
                setError("Error deleting post: ${e.message}")
                setLoading(false)
            }
        }
    }

    // Helper method to broadcast post changes
    private fun broadcastPostChange(action: String, postId: String, photoUrl: String? = null) {
        try {
            val context = getApplication<Application>().applicationContext
            val intent = android.content.Intent(action).apply {
                putExtra("postId", postId)
                if (photoUrl != null) {
                    putExtra("photoUrl", photoUrl)
                }
            }
            context.sendBroadcast(intent)
            Log.d("ProfileViewModel", "Broadcast sent: $action for post $postId")
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error broadcasting post change: ${e.message}")
        }
    }

    /**
     * Force refresh user posts with latest data from Firestore
     */
    fun refreshUserPosts(userId: String) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val targetUserId = userId 
                
                Log.d("ProfileViewModel", "Force refreshing posts for user: $targetUserId")
                
                // Check if posts have been actually modified before setting global refresh flags
                var postsChanged = false
                
                // Clear room cache for this user's posts
                try {
                    // We don't have a direct method to clear only a user's posts, but we can
                    // get the user's posts and delete them individually
                    val userPosts = feedRepository.getFeedItemsByUserId(targetUserId)
                    for (post in userPosts) {
                        feedRepository.deleteFeedItem(post.id)
                    }
                    Log.d("ProfileViewModel", "Cleared ${userPosts.size} posts from cache for user $targetUserId")
                } catch (e: Exception) {
                    Log.e("ProfileViewModel", "Error clearing user posts cache: ${e.message}")
                }
                
                // Fetch fresh from Firestore
                val posts = feedRepository.loadUserPosts(targetUserId)
                
                // Ensure the user data in posts is up-to-date
                val updatedPosts = refreshPostUserData(posts, targetUserId)
                
                // Check if there were actual changes to the posts
                postsChanged = posts.isNotEmpty() && (updatedPosts != posts)
                
                Log.d("ProfileViewModel", "Refreshed and found ${posts.size} posts, changes detected: $postsChanged")
                _userPosts.value = updatedPosts
                setLoading(false)
                
                // Only signal that feed should be refreshed if we've detected actual changes
                if (postsChanged) {
                    Log.d("ProfileViewModel", "Setting global feed refresh flag due to post changes")
                    GlobalState.shouldRefreshFeed = true
                } else {
                    Log.d("ProfileViewModel", "No post changes detected, skipping global feed refresh")
                }
                
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error refreshing user posts: ${e.message}", e)
                setError("Error refreshing posts: ${e.message}")
                setLoading(false)
            }
        }
    }
    
    /**
     * Ensure the user data in posts is up-to-date
     */
    private suspend fun refreshPostUserData(posts: List<FeedItem>, userId: String): List<FeedItem> {
        if (posts.isEmpty()) return emptyList()
        
        try {
            // Get the latest user data 
            val userData = userRepository.getUserData(userId, forceRefresh = true)
            
            if (userData != null && !userData.name.isNullOrEmpty()) {
                Log.d("ProfileViewModel", "Updating posts with user data: name=${userData.name}, photoUrl=${userData.profilePhotoUrl}")
                
                // Update all posts with the current user data
                return posts.map { post ->
                    if (post.userId == userId) {
                        // Only update if data actually changed
                        if (post.userName != userData.name || post.userPhotoUrl != userData.profilePhotoUrl) {
                            // Create a new post with updated data
                            post.copy(
                                userName = userData.name,
                                userPhotoUrl = userData.profilePhotoUrl
                            )
                        } else {
                            post // Return the original post if no changes needed
                        }
                    } else {
                        post // Return the original post for other users
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error refreshing post user data: ${e.message}")
            // Don't rethrow, just continue with original posts
        }
        
        return posts
    }
    
    /**
     * Update comment count for a specific post
     */
    fun updateCommentCount(postId: String, count: Int) {
        val currentPosts = _userPosts.value?.toMutableList() ?: return
        val index = currentPosts.indexOfFirst { it.id == postId }
        
        if (index != -1) {
            // Update the post in our list
            currentPosts[index].commentCount = count
            _userPosts.value = currentPosts
            
            // Update in Room database
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    feedRepository.updateCommentCount(postId, count)
                    Log.d("ProfileViewModel", "Updated comment count for post $postId: $count")
                } catch (e: Exception) {
                    Log.e("ProfileViewModel", "Error updating comment count: ${e.message}")
                }
            }
        }
    }

    /**
     * Update likes for a post in the profile view
     * This handles toggling a like from the profile UI
     */
    fun toggleLike(postId: String) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                
                // Get current post data
                val postRef = db.collection("Posts").document(postId)
                val postSnapshot = postRef.get().await()
                val likes = postSnapshot.get("likes") as? List<String> ?: listOf()
                
                // Check if user already liked the post
                val userLiked = likes.contains(userId)
                
                // Toggle the like
                val updatedLikes = if (userLiked) {
                    likes.filter { it != userId }
                } else {
                    if (!likes.contains(userId)) likes + userId else likes
                }
                
                // Update in Firestore
                postRef.update(
                    mapOf(
                        "likes" to updatedLikes,
                        "likeCount" to updatedLikes.size
                    )
                ).await()
                
                // Update local data
                updatePostLikes(postId, updatedLikes)
                
                // Broadcast the change to ensure all fragments are updated
                val intent = Intent("com.eaor.coffeefee.LIKE_UPDATED")
                intent.putExtra("postId", postId)
                intent.putExtra("likeCount", updatedLikes.size)
                getApplication<Application>().sendBroadcast(intent)
                
                // Synchronize with Room database to ensure consistent state
                feedRepository.updateLikes(postId, updatedLikes)
                
                Log.d("ProfileViewModel", "Like toggled for post $postId, new state: ${!userLiked}")
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error toggling like: ${e.message}")
            }
        }
    }
    
    /**
     * Update post likes in the ViewModel and Room
     */
    private fun updatePostLikes(postId: String, newLikes: List<String>) {
        val currentPosts = _userPosts.value?.toMutableList() ?: return
        val index = currentPosts.indexOfFirst { it.id == postId }
        
        if (index != -1) {
            val post = currentPosts[index]
            // Use the FeedItem's updateLikes method for consistency
            post.updateLikes(newLikes)
            currentPosts[index] = post
            _userPosts.value = currentPosts
            
            // Notify adapter with payload for efficient UI update
            _payload.value = Pair(index, FeedAdapter.LIKE_COUNT)
            
            Log.d("ProfileViewModel", "Updated local post like state: post=$postId, likeCount=${post.likeCount}")
        }
    }
    
    /**
     * Update likes from external sources (broadcasts from other fragments)
     */
    fun updateLikesFromExternal(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting updateLikesFromExternal for postId: $postId")
                val userId = auth.currentUser?.uid

                // Get the latest likes data from Firestore
                val postDoc = db.collection("Posts").document(postId).get().await()
                if (postDoc.exists()) {
                    val postData = postDoc.data
                    if (postData != null) {
                        val likes = postData["likes"] as? List<String> ?: listOf()
                        val likeCount = likes.size
                        Log.d(TAG, "Firestore data for post $postId: likes=$likes, count=$likeCount")

                        // Update in Room database
                        feedRepository.updateLikes(postId, likes)
                        feedRepository.updateLikeCount(postId, likeCount)
                        Log.d(TAG, "Updated Room database for post $postId")

                        // Update in-memory posts
                        val updatedPosts = _userPosts.value?.map { post ->
                            if (post.id == postId) {
                                post.updateLikes(likes)
                                post.likeCount = likeCount
                                post.isLikedByCurrentUser = userId != null && post.hasUserLiked(userId)
                                Log.d(TAG, "Updated in-memory post $postId, liked by user: ${post.isLikedByCurrentUser}")
                                post
                            } else {
                                post
                            }
                        } ?: listOf()

                        withContext(Dispatchers.Main) {
                            _userPosts.value = updatedPosts
                            // Ensure GlobalState is updated
                            GlobalState.postsWereChanged = true
                            GlobalState.shouldRefreshFeed = true
                            Log.d(TAG, "Updated _userPosts and set GlobalState flags")
                        }
                    }
                } else {
                    Log.e(TAG, "Post document $postId doesn't exist in Firestore")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in updateLikesFromExternal: ${e.message}", e)
            }
        }
    }

    /**
     * Get user profile data from repository with caching
     * @param userId The user ID to get profile for
     * @param forceRefresh Whether to force refresh from network
     * @param maxAgeMinutes Maximum age of cached data in minutes
     */
    fun getUserProfile(userId: String, forceRefresh: Boolean = false, maxAgeMinutes: Int = 30) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                Log.d("ProfileViewModel", "Getting user profile for $userId (forceRefresh=$forceRefresh)")
                
                // Get user data from repository with caching logic
                val user = userRepository.getUserData(userId, forceRefresh, maxAgeMinutes)
                
                if (user != null) {
                    Log.d("ProfileViewModel", "Loaded user: ${user.name}")
                    
                    // Build map with non-nullable values for required fields
                    val userMap = HashMap<String, Any>()
                    userMap["uid"] = user.uid ?: ""
                    userMap["name"] = user.name ?: ""
                    userMap["email"] = user.email ?: ""
                    userMap["profilePhotoUrl"] = user.profilePhotoUrl ?: ""
                    userMap["lastUpdatedTimestamp"] = user.lastUpdatedTimestamp
                    
                    // Update LiveData with user data
                    _userData.value = userMap
                    
                    // Trigger refresh for all posts by this user since user data has changed
                    if (forceRefresh) {
                        refreshUserPosts(userId)
                        
                        // Also mark global state so other fragments can refresh
                        GlobalState.profileDataChanged = true
                    }
                } else {
                    Log.e("ProfileViewModel", "No user data found for $userId")
                    _errorMessage.value = "User not found"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading user profile: ${e.message}")
                _errorMessage.value = "Failed to load profile: ${e.message}"
            } finally {
                // Always update loading state
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresh a specific post rather than all user posts
     */
    fun refreshSpecificPost(postId: String) {
        viewModelScope.launch {
            try {
                Log.d("ProfileViewModel", "Refreshing specific post: $postId")
                // Get the post from Firestore
                val postRef = db.collection("Posts").document(postId)
                val postSnapshot = postRef.get().await()
                
                if (postSnapshot.exists()) {
                    // Map to FeedItem
                    val post = feedRepository.mapDocumentToFeedItem(postSnapshot)
                    
                    if (post != null) {
                        // Update in Room database
                        feedRepository.insertFeedItem(post)
                        
                        // Update in-memory post list if it contains this post
                        val currentPosts = _userPosts.value?.toMutableList() ?: mutableListOf()
                        val index = currentPosts.indexOfFirst { it.id == postId }
                        
                        if (index != -1) {
                            // Replace the post at the same position
                            currentPosts[index] = post
                            _userPosts.value = currentPosts
                            
                            // Notify adapter to update only this item
                            _payload.value = Pair(index, "all_data")
                            
                            Log.d("ProfileViewModel", "Updated specific post in profile: $postId")
                        }
                    }
                } else {
                    // Post no longer exists, remove it
                    removePostFromUserPosts(postId)
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error refreshing specific post: ${e.message}")
            }
        }
    }
    
    /**
     * Remove a specific post from user posts list
     */
    fun removePostFromUserPosts(postId: String) {
        viewModelScope.launch {
            try {
                Log.d("ProfileViewModel", "Removing post from user posts: $postId")
                
                // Remove from Room database
                feedRepository.deleteFeedItem(postId)
                
                // Remove from in-memory list
                val currentPosts = _userPosts.value?.toMutableList() ?: return@launch
                val index = currentPosts.indexOfFirst { it.id == postId }
                
                if (index != -1) {
                    currentPosts.removeAt(index)
                    _userPosts.value = currentPosts
                    
                    Log.d("ProfileViewModel", "Removed post from user posts: $postId")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error removing post from user posts: ${e.message}")
            }
        }
    }
} 