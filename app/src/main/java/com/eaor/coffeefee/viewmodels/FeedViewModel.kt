package com.eaor.coffeefee.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.CoffeefeeApplication
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.models.FeedItem
import com.eaor.coffeefee.repositories.FeedRepository
import com.eaor.coffeefee.repositories.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.util.Log
import kotlinx.coroutines.CancellationException
import com.eaor.coffeefee.GlobalState

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val _feedPosts = MutableLiveData<List<FeedItem>>()
    val feedPosts: LiveData<List<FeedItem>> = _feedPosts

    private var lastVisible: DocumentSnapshot? = null
    private val loadedPostIds = HashSet<String>()
    private var forceReload = false
    private val feedRepository: FeedRepository
    private lateinit var userRepository: UserRepository
    
    // Firebase instances
    protected val auth = FirebaseAuth.getInstance()
    protected val db = FirebaseFirestore.getInstance()
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error message
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        // Initialize Repository in the constructor
        val database = AppDatabase.getDatabase(application)
        // Create a temporary reference to the feedItemDao
        val feedItemDao = database.feedItemDao()
        // We'll create the actual FeedRepository after the UserRepository is set
        // Create a temporary instance with minimal functionality
        feedRepository = FeedRepository(feedItemDao, db, 
            com.eaor.coffeefee.repositories.UserRepository(database.userDao(), db))
        
        Log.d("FeedViewModel", "FeedRepository initialized in constructor")
    }

    // Set the user repository (will be called from fragment)
    fun setUserRepository(repository: UserRepository) {
        this.userRepository = repository
    }

    protected fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    protected fun setError(message: String?) {
        _errorMessage.value = message
    }

    fun loadInitialPosts() {
        if (_feedPosts.value?.isNotEmpty() == true && !forceReload) {
            Log.d("FeedViewModel", "Using existing posts in memory, skipping network load")
            
            // Even if skipping network load, check if we need to refresh user data
            val postsWithMissingUserData = _feedPosts.value?.any { 
                it.userName.isNullOrEmpty() || it.userName == "Unknown User" 
            } ?: false
            
            if (postsWithMissingUserData) {
                Log.d("FeedViewModel", "Some posts have missing user data, fetching user data only")
                fetchUserDataForPosts(_feedPosts.value ?: emptyList())
            }
            
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
                    
                    // Log any posts with missing user data for debugging
                    val postsWithMissingUserData = posts.filter { 
                        it.userName.isNullOrEmpty() || it.userName == "Unknown User" 
                    }
                    if (postsWithMissingUserData.isNotEmpty()) {
                        Log.d("FeedViewModel", "${postsWithMissingUserData.size} posts have missing user data")
                    }
                    
                    // Update LiveData immediately for UI display
                    _feedPosts.value = posts
                    
                    // Invalidate Picasso cache for post images
                    posts.forEach { post ->
                        post.photoUrl?.let { url ->
                            if (url.isNotEmpty()) {
                                com.squareup.picasso.Picasso.get().invalidate(url)
                            }
                        }
                    }
                    
                    // Fetch user data for posts (this will update UI when done)
                    fetchUserDataForPosts(posts)
                    
                    // Cache posts in Room
                    cachePosts()
                    
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
            Log.d("FeedViewModel", "Found post at index $index, updating comment count to $count")
            
            // First update the post in our mutable list without replacing entire list
            currentPosts[index].commentCount = count
            
            // Store reference to the current list
            _feedPosts.postValue(currentPosts)
            
            // Update in Room database
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    feedRepository.updateCommentCount(postId, count)
                    Log.d("FeedViewModel", "Updated comment count in Room database: post=$postId, count=$count")
                } catch (e: Exception) {
                    Log.e("FeedViewModel", "Error updating comment count in Room: ${e.message}")
                }
            }
        } else {
            Log.e("FeedViewModel", "Could not find post $postId in current feed posts")
        }
    }

    fun refreshPosts() {
        if (isLoading.value == true) return
        setLoading(true)
        
        Log.d("FeedViewModel", "Starting refreshPosts with force reload")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Reset pagination and force reload flag
                lastVisible = null
                forceReload = true
                
                // Always clear the loaded post IDs set when doing a full refresh
                loadedPostIds.clear()
                
                // Store current post IDs to check for changes later
                val previousPostIds = _feedPosts.value?.map { it.id }?.toSet() ?: emptySet()
                
                // Clear the Room database cache to ensure we get fresh data
                feedRepository.clearAllFeedItems()
                
                // Load fresh posts from Firestore with forceRefresh = true to bypass caching
                val freshPosts = feedRepository.loadInitialPosts(forceRefresh = true)
                
                // Check if posts have changed
                val newPostIds = freshPosts.map { it.id }.toSet()
                val hasNewPosts = newPostIds.size != previousPostIds.size || !newPostIds.containsAll(previousPostIds)
                
                if (hasNewPosts) {
                    Log.d("FeedViewModel", "Posts have changed during refresh (${previousPostIds.size} vs ${newPostIds.size})")
                }
                
                // Update the UI on the main thread
                withContext(Dispatchers.Main) {
                    if (freshPosts.isNotEmpty()) {
                        // Update loaded post IDs
                        loadedPostIds.clear()
                        freshPosts.forEach { loadedPostIds.add(it.id) }
                        
                        // Always invalidate all image caches for fresh posts
                        freshPosts.forEach { post ->
                            // Invalidate post image
                            post.photoUrl?.let { url ->
                                if (url.isNotEmpty()) {
                                    try {
                                        com.squareup.picasso.Picasso.get().invalidate(url)
                                    } catch (e: Exception) {
                                        Log.e("FeedViewModel", "Error invalidating post image: ${e.message}")
                                    }
                                }
                            }
                            
                            // Invalidate user profile image
                            post.userPhotoUrl?.let { url ->
                                if (url.isNotEmpty()) {
                                    try {
                                        com.squareup.picasso.Picasso.get().invalidate(url)
                                    } catch (e: Exception) {
                                        Log.e("FeedViewModel", "Error invalidating profile image: ${e.message}")
                                    }
                                }
                            }
                        }
                        
                        // Update LiveData with new posts
                        _feedPosts.value = freshPosts
                        
                        // Fetch user data for posts with forced refresh
                        fetchUserDataForPosts(freshPosts)
                        
                        // Save to Room database
                        cachePosts()
                        
                        Log.d("FeedViewModel", "Posts refreshed - loaded ${freshPosts.size} fresh posts")
                    } else {
                        // If no posts were found, show empty list
                        _feedPosts.value = emptyList()
                        Log.d("FeedViewModel", "Posts refreshed - no posts found")
                    }
                    setLoading(false)
                }
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Error refreshing posts: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    setError("Error refreshing feed: ${e.message}")
                    setLoading(false)
                }
            }
        }
    }

    // Improved cachePosts method
    fun cachePosts() {
        viewModelScope.launch {
            try {
                val posts = _feedPosts.value ?: return@launch
                if (posts.isEmpty()) {
                    Log.d("FeedViewModel", "No posts to cache")
                    return@launch
                }
                
                // Ensure we have all necessary data before caching
                for (post in posts) {
                    if (post.id.isEmpty()) {
                        Log.e("FeedViewModel", "Post with empty ID found, skipping cache")
                        return@launch
                    }
                }
                
                // Use the new optimized method that ensures posts have up-to-date user data
                feedRepository.cacheFeedItems(posts)
                Log.d("FeedViewModel", "Called cacheFeedItems with ${posts.size} posts")
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
                        
                        // Check for missing user data in cached posts
                        val postsWithMissingUserData = cachedPosts.any { 
                            it.userName.isNullOrEmpty() || it.userName == "Unknown User" 
                        }
                        
                        // Update LiveData right away for UI display
                        _feedPosts.value = cachedPosts
                        
                        // Add to loaded post IDs
                        cachedPosts.forEach { loadedPostIds.add(it.id) }
                        
                        // Reset last visible for next pagination
                        lastVisible = null
                        
                        // Always fetch user data for cached posts
                        // This will ensure any missing user data is filled in
                        fetchUserDataForPosts(cachedPosts)
                        
                        // If we had missing user data, schedule a refresh from network later
                        if (postsWithMissingUserData) {
                            Log.d("FeedViewModel", "Some cached posts have missing user data, will refresh from network")
                            forceReload = true
                        }
                    } else {
                        Log.d("FeedViewModel", "No posts found in cache")
                    }
                }
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Error loading cached posts: ${e.message}", e)
            }
        }
    }

    // Improved function to fetch user data for posts with optimized error handling
    private fun fetchUserDataForPosts(posts: List<FeedItem>) {
        if (posts.isEmpty()) return
        
        viewModelScope.launch {
            try {
                // Create a set of unique user IDs to reduce redundant queries
                val userIds = posts.mapNotNull { 
                    if (it.userId.isNullOrEmpty()) {
                        Log.w("FeedViewModel", "Post ${it.id} has empty userId, skipping user data fetch")
                        null
                    } else {
                        it.userId
                    }
                }.toSet()
                
                Log.d("FeedViewModel", "Fetching user data for ${userIds.size} unique users")
                
                // Check if we have posts with missing user information
                val postsWithMissingData = posts.filter { 
                    it.userName.isNullOrEmpty() || it.userName == "Unknown User" || it.userPhotoUrl.isNullOrEmpty()
                }
                
                // If any posts have missing data, log them
                if (postsWithMissingData.isNotEmpty()) {
                    Log.d("FeedViewModel", "Found ${postsWithMissingData.size} posts with missing user data")
                    postsWithMissingData.forEach { post ->
                        Log.d("FeedViewModel", "Post ${post.id} has missing data: userId=${post.userId}, name=${post.userName}, photoUrl=${post.userPhotoUrl}")
                    }
                }
                
                // Get IDs of users with missing data
                val userIdsWithMissingData = postsWithMissingData.map { it.userId }.toSet()
                
                // Always force refresh for users with missing data
                val forceRefreshUserIds = if (userIdsWithMissingData.isNotEmpty()) {
                    userIdsWithMissingData
                } else {
                    emptySet()
                }
                
                // Process each user separately for better error isolation
                for (userId in userIds) {
                    try {
                        // Force refresh if this user has missing data
                        val needsForceRefresh = userId in forceRefreshUserIds
                        
                        if (needsForceRefresh) {
                            Log.d("FeedViewModel", "Force refreshing user data for $userId")
                            // Clear the cache first for users with missing data
                            userRepository.clearUserCache(userId)
                        }
                        
                        // Get fresh user data
                        val userData = userRepository.getUserData(userId, forceRefresh = needsForceRefresh)
                        
                        if (userData != null && !userData.name.isNullOrEmpty()) {
                            // Update all posts for this user
                            Log.d("FeedViewModel", "Got user data for $userId: name=${userData.name}, photoUrl=${userData.profilePhotoUrl}")
                            updatePostsForUser(userId, userData.name, userData.profilePhotoUrl)
                        } else {
                            Log.w("FeedViewModel", "Failed to get user data for $userId or name is empty")
                        }
                    } catch (e: Exception) {
                        Log.e("FeedViewModel", "Error processing user $userId: ${e.message}")
                        // Continue with other users instead of failing completely
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("FeedViewModel", "Error fetching user data for posts: ${e.message}")
            }
        }
    }
    
    // Helper method to update all posts for a user
    private fun updatePostsForUser(userId: String, userName: String, userPhotoUrl: String?) {
        // First update in-memory posts
        val currentPosts = _feedPosts.value?.toMutableList() ?: mutableListOf()
        var updated = false
        
        for (i in currentPosts.indices) {
            if (currentPosts[i].userId == userId) {
                // Update only if data is actually different
                if (currentPosts[i].userName != userName || currentPosts[i].userPhotoUrl != userPhotoUrl) {
                    currentPosts[i] = currentPosts[i].copy(
                        userName = userName,
                        userPhotoUrl = userPhotoUrl
                    )
                    updated = true
                }
            }
        }
        
        // Update LiveData if changes were made
        if (updated) {
            _feedPosts.value = currentPosts
            Log.d("FeedViewModel", "Updated in-memory posts for user $userId")
        }
    }

    /**
     * Refreshes user data in the feed only if there are actual changes
     */
    fun refreshUserData(forceRefresh: Boolean = false) {
        if (!forceRefresh && !GlobalState.shouldRefreshFeed) {
            Log.d("FeedViewModel", "Skipping user data refresh - no changes detected")
            return
        }

        viewModelScope.launch {
            setLoading(true)
            try {
                // Get the current posts that might need user data refresh
                val currentPosts = _feedPosts.value ?: listOf()
                if (currentPosts.isEmpty()) {
                    Log.d("FeedViewModel", "No posts to refresh user data for")
                    setLoading(false)
                    return@launch
                }
                
                // Get set of unique user IDs from posts
                val userIds = currentPosts.mapNotNull { 
                    if (it.userId.isNullOrEmpty()) null else it.userId 
                }.toSet()
                
                if (userIds.isEmpty()) {
                    Log.d("FeedViewModel", "No valid user IDs found in posts")
                    setLoading(false)
                    return@launch
                }
                
                Log.d("FeedViewModel", "Refreshing user data for ${userIds.size} users in ${currentPosts.size} posts")
                
                // Use forced refresh if specifically requested
                val usersMap = userRepository.getUsersMapByIds(userIds.toList(), forceRefresh = forceRefresh)
                
                // Track if we made any updates
                var updatedCount = 0
                
                // Update user data in posts
                val updatedPosts = currentPosts.map { post ->
                    if (post.userId.isNotEmpty()) {
                        val user = usersMap[post.userId]
                        
                        // Only update if we found user data and it's different from what's in the post
                        if (user != null && !user.name.isNullOrEmpty() && 
                            (post.userName != user.name || post.userPhotoUrl != user.profilePhotoUrl)) {
                            
                            // Count this as an update
                            updatedCount++
                            
                            // Create a new post with updated user data
                            post.copy(
                                userName = user.name,
                                userPhotoUrl = user.profilePhotoUrl
                            )
                        } else {
                            // Keep post as is
                            post
                        }
                    } else {
                        // Keep post as is if no user ID
                        post
                    }
                }
                
                // Only update LiveData if we made changes
                if (updatedCount > 0) {
                    Log.d("FeedViewModel", "Updated user data for $updatedCount posts")
                    _feedPosts.value = updatedPosts
                    
                    // Cache the updated posts
                    cachePosts()
                } else {
                    Log.d("FeedViewModel", "No user data updates needed")
                }
                
                // Reset the refresh flag
                GlobalState.shouldRefreshFeed = false
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("FeedViewModel", "Error refreshing user data: ${e.message}")
                setError("Error refreshing user data: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }
} 