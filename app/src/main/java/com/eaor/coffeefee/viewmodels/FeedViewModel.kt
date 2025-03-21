package com.eaor.coffeefee.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.models.FeedItem
import com.eaor.coffeefee.repository.FeedRepository
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.util.Log

class FeedViewModel : BaseViewModel() {
    private val _feedPosts = MutableLiveData<List<FeedItem>>()
    val feedPosts: LiveData<List<FeedItem>> = _feedPosts

    private var lastVisible: DocumentSnapshot? = null
    private val loadedPostIds = HashSet<String>()
    private var forceReload = false
    private lateinit var feedRepository: FeedRepository

    // Set the repository (will be called from fragment)
    fun setRepository(repository: FeedRepository) {
        feedRepository = repository
    }

    fun loadInitialPosts() {
        if (_feedPosts.value?.isNotEmpty() == true && !forceReload) {
            Log.d("FeedViewModel", "Using existing posts in memory, skipping network load")
            return
        }
        
        if (isLoading.value == true) return
        setLoading(true)
        
        forceReload = false
        
        Log.d("FeedViewModel", "Loading initial posts...")
        
        viewModelScope.launch {
            try {
                // Get posts from the repository
                val posts = feedRepository.loadInitialPosts()
                
                if (posts.isNotEmpty()) {
                    // Set last visible for pagination
                    lastVisible = db.collection("Posts")
                        .document(posts.last().id)
                        .get()
                        .await()
                    
                    // Clear and add to loaded post IDs
                    loadedPostIds.clear()
                    posts.forEach { loadedPostIds.add(it.id) }
                    
                    // Update LiveData
                    _feedPosts.value = posts
                    
                    // Fetch user data for posts
                    fetchUserDataForPosts(posts)
                    
                    Log.d("FeedViewModel", "Loaded ${posts.size} initial posts")
                } else {
                    Log.d("FeedViewModel", "No posts found")
                }
                
                setLoading(false)
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Error loading posts: ${e.message}", e)
                setError("Error loading posts: ${e.message}")
                setLoading(false)
            }
        }
    }

    fun loadMorePosts() {
        if (isLoading.value == true || lastVisible == null) return
        setLoading(true)
        
        viewModelScope.launch {
            try {
                // Get more posts from the repository
                val newPosts = feedRepository.loadMorePosts(lastVisible!!)
                
                if (newPosts.isNotEmpty()) {
                    // Update last visible for pagination
                    lastVisible = db.collection("Posts")
                        .document(newPosts.last().id)
                        .get()
                        .await()
                    
                    // Filter out already loaded posts and add new ones
                    val filteredPosts = newPosts.filter { !loadedPostIds.contains(it.id) }
                    filteredPosts.forEach { loadedPostIds.add(it.id) }
                    
                    // Update LiveData
                    val currentPosts = _feedPosts.value ?: listOf()
                    _feedPosts.value = currentPosts + filteredPosts
                    
                    // Fetch user data for new posts
                    fetchUserDataForPosts(filteredPosts)
                    
                    Log.d("FeedViewModel", "Loaded ${filteredPosts.size} more posts")
                } else {
                    Log.d("FeedViewModel", "No more posts found")
                }
                
                setLoading(false)
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Error loading more posts: ${e.message}", e)
                setError("Error loading more posts: ${e.message}")
                setLoading(false)
            }
        }
    }

    fun toggleLike(postId: String) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                
                // Get current post data
                val postRef = db.collection("Posts").document(postId)
                val postSnapshot = postRef.get().await()
                val likes = postSnapshot.get("likes") as? List<String> ?: listOf()
                
                // Toggle the like
                val updatedLikes = if (likes.contains(userId)) {
                    likes.filter { it != userId }
                } else {
                    likes + userId
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
            } catch (e: Exception) {
                setError("Error toggling like: ${e.message}")
            }
        }
    }

    private fun updatePostLikes(postId: String, newLikes: List<String>) {
        val currentPosts = _feedPosts.value?.toMutableList() ?: return
        val index = currentPosts.indexOfFirst { it.id == postId }
        
        if (index != -1) {
            val post = currentPosts[index]
            post.updateLikes(newLikes)
            post.likeCount = newLikes.size
            currentPosts[index] = post
            _feedPosts.value = currentPosts
        }
    }

    fun updateCommentCount(postId: String, count: Int) {
        val currentPosts = _feedPosts.value?.toMutableList() ?: return
        val index = currentPosts.indexOfFirst { it.id == postId }
        
        if (index != -1) {
            currentPosts[index].commentCount = count
            _feedPosts.value = currentPosts
            
            // Update in Room database
            viewModelScope.launch(Dispatchers.IO) {
                feedRepository.updateCommentCount(postId, count)
            }
        }
    }

    fun refreshPosts() {
        lastVisible = null
        loadedPostIds.clear()
        forceReload = true
        loadInitialPosts()
    }

    // Add the missing cachePosts method
    fun cachePosts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val posts = _feedPosts.value ?: return@launch
                feedRepository.insertFeedItems(posts)
                Log.d("FeedViewModel", "Cached ${posts.size} posts to Room database")
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Error caching posts: ${e.message}", e)
            }
        }
    }

    fun loadCachedPosts() {
        if (_feedPosts.value?.isNotEmpty() == true) {
            Log.d("FeedViewModel", "Already have posts in memory, skipping cache load")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cachedPosts = feedRepository.getAllFeedItems()
                
                withContext(Dispatchers.Main) {
                    if (cachedPosts.isNotEmpty()) {
                        Log.d("FeedViewModel", "Loaded ${cachedPosts.size} posts from Room cache")
                        _feedPosts.value = cachedPosts
                        
                        // Add to loaded post IDs
                        cachedPosts.forEach { loadedPostIds.add(it.id) }
                        
                        // Reset last visible for next pagination
                        lastVisible = null
                        
                        // Fetch user data for cached posts
                        fetchUserDataForPosts(cachedPosts)
                    } else {
                        Log.d("FeedViewModel", "No posts found in cache")
                    }
                }
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Error loading cached posts: ${e.message}", e)
            }
        }
    }

    // Updated function to fetch user data for posts
    private fun fetchUserDataForPosts(posts: List<FeedItem>) {
        if (posts.isEmpty()) return
        
        viewModelScope.launch {
            try {
                // Create a set of unique user IDs to reduce redundant queries
                val userIds = posts.map { it.userId }.toSet()
                
                userIds.forEach { userId ->
                    try {
                        // First check "Users" collection (uppercase)
                        val userDocUppercase = db.collection("Users").document(userId).get().await()
                        
                        if (userDocUppercase.exists()) {
                            // Try to get profile photo URL from different possible fields
                            val profileUrl = userDocUppercase.getString("profilePhotoUrl")
                                ?: userDocUppercase.getString("profilePictureUrl")
                                ?: userDocUppercase.getString("photoUrl")
                            
                            val userName = userDocUppercase.getString("name") ?: "Unknown User"
                            
                            Log.d("FeedViewModel", "Fetched user data from Users for $userId: $userName, $profileUrl")
                            
                            // Update all posts by this user
                            updatePostsWithUserData(userId, userName, profileUrl)
                        } else {
                            // Try "users" collection (lowercase) if uppercase fails
                            val userDocLowercase = db.collection("users").document(userId).get().await()
                            
                            if (userDocLowercase.exists()) {
                                // Try to get profile photo URL from different possible fields
                                val profileUrl = userDocLowercase.getString("profilePhotoUrl")
                                    ?: userDocLowercase.getString("profilePictureUrl")
                                    ?: userDocLowercase.getString("photoUrl")
                                
                                val userName = userDocLowercase.getString("name") ?: "Unknown User"
                                
                                Log.d("FeedViewModel", "Fetched user data from users for $userId: $userName, $profileUrl")
                                
                                // Update all posts by this user
                                updatePostsWithUserData(userId, userName, profileUrl)
                            } else {
                                // If user document doesn't exist in both collections, use Firebase Auth as fallback for current user
                                if (userId == auth.currentUser?.uid) {
                                    val currentUserName = auth.currentUser?.displayName ?: "You"
                                    val currentUserPhotoUrl = auth.currentUser?.photoUrl?.toString()
                                    
                                    updatePostsWithUserData(userId, currentUserName, currentUserPhotoUrl)
                                } else {
                                    // Default for unknown users
                                    updatePostsWithUserData(userId, "Unknown User", null)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FeedViewModel", "Error fetching user data for $userId: ${e.message}")
                        // Continue with next user even if one fails
                    }
                }
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Error in fetchUserDataForPosts: ${e.message}")
            }
        }
    }
    
    private fun updatePostsWithUserData(userId: String, userName: String, userPhotoUrl: String?) {
        val currentPosts = _feedPosts.value?.toMutableList() ?: return
        var updated = false
        
        // Update all posts by this user
        currentPosts.forEachIndexed { index, post ->
            if (post.userId == userId) {
                post.userName = userName
                post.userPhotoUrl = userPhotoUrl
                updated = true
            }
        }
        
        // Only update LiveData if we made changes
        if (updated) {
            _feedPosts.value = currentPosts
        }
    }

    // Add method to refresh feed items with latest user data
    fun refreshUserData() {
        viewModelScope.launch {
            try {
                val posts = _feedPosts.value ?: return@launch
                
                val currentUserUid = auth.currentUser?.uid ?: return@launch
                val currentUserDoc = db.collection("Users").document(currentUserUid).get().await()
                
                if (currentUserDoc.exists()) {
                    val profileUrl = currentUserDoc.getString("profilePhotoUrl")
                        ?: currentUserDoc.getString("profilePictureUrl")
                        ?: currentUserDoc.getString("photoUrl")
                        ?: auth.currentUser?.photoUrl?.toString()
                    
                    val userName = currentUserDoc.getString("name") 
                        ?: auth.currentUser?.displayName
                        ?: "You"
                    
                    // Update all posts by the current user
                    updatePostsWithUserData(currentUserUid, userName, profileUrl)
                    
                    // Also update posts in the repository
                    feedRepository.updateUserDataInPosts(currentUserUid, userName, profileUrl)
                }
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Error refreshing user data: ${e.message}")
            }
        }
    }
} 