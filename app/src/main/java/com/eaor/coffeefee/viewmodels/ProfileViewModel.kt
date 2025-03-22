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

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = FirebaseStorage.getInstance()
    
    private val _userData = MutableLiveData<Map<String, Any>>()
    val userData: LiveData<Map<String, Any>> = _userData
    
    private val _userPosts = MutableLiveData<List<FeedItem>>()
    val userPosts: LiveData<List<FeedItem>> = _userPosts
    
    private val _updateSuccess = MutableLiveData<Boolean>()
    val updateSuccess: LiveData<Boolean> = _updateSuccess
    
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
    
    fun loadUserPosts(userId: String? = null) {
        setLoading(true)
        
        Log.d("ProfileViewModel", "Starting to load user posts")
        
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
                
                // If no local results, fetch from Firestore
                if (posts.isEmpty()) {
                    Log.d("ProfileViewModel", "No posts found in Room, fetching from Firestore")
                    posts = feedRepository.loadUserPosts(targetUserId)
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
                GlobalState.shouldRefreshProfile = true
                
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
                
                // Delete from Firestore first
                db.collection("Posts").document(postId).delete().await()
                
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
} 