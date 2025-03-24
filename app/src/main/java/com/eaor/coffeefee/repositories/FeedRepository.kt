package com.eaor.coffeefee.repositories

import android.util.Log
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.data.FeedItemDao
import com.eaor.coffeefee.data.FeedItemEntity
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.models.FeedItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import com.eaor.coffeefee.GlobalState
import com.eaor.coffeefee.data.UserDao

class FeedRepository(
    // Make the DAO public so it can be accessed by the ViewModel
    val feedItemDao: FeedItemDao,
    private val firestore: FirebaseFirestore,
    private val userRepository: com.eaor.coffeefee.repositories.UserRepository
) {
    private val TAG = "FeedRepository"
    
    // Local database operations
    suspend fun insertFeedItem(feedItem: FeedItem) {
        try {
            val entity = FeedItemEntity.fromFeedItem(feedItem)
            feedItemDao.insertFeedItem(entity)
            Log.d(TAG, "Inserted feed item to Room: ${feedItem.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting feed item: ${e.message}", e)
        }
    }

    suspend fun insertFeedItems(feedItems: List<FeedItem>) {
        try {
            val entities = feedItems.map { FeedItemEntity.fromFeedItem(it) }
            feedItemDao.insertFeedItems(entities)
            Log.d(TAG, "Inserted ${feedItems.size} feed items to Room")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting feed items: ${e.message}", e)
        }
    }

    suspend fun getAllFeedItems(): List<FeedItem> {
        return try {
            val entities = feedItemDao.getAllFeedItems()
            val feedItems = entities.map { it.toFeedItem() }
            Log.d(TAG, "Retrieved ${feedItems.size} feed items from Room")
            feedItems
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all feed items: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getFeedItemsByUserId(userId: String): List<FeedItem> {
        return try {
            val entities = feedItemDao.getFeedItemsByUserId(userId)
            val feedItems = entities.map { it.toFeedItem() }
            Log.d(TAG, "Retrieved ${feedItems.size} feed items for user $userId from Room")
            feedItems
        } catch (e: Exception) {
            Log.e(TAG, "Error getting feed items for user $userId: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun updateCommentCount(postId: String, count: Int) {
        try {
            feedItemDao.updateCommentCount(postId, count)
            Log.d(TAG, "Updated comment count for post $postId to $count")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating comment count: ${e.message}", e)
        }
    }

    /**
     * Update like count for a post in Room database
     */
    suspend fun updateLikeCount(postId: String, count: Int) {
        try {
            feedItemDao.updateLikeCount(postId, count)
            Log.d(TAG, "Updated like count for post $postId to $count")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating like count: ${e.message}", e)
        }
    }
    
    /**
     * Update like count and likes list for a post in Room database
     */
    suspend fun updateLikes(postId: String, likes: List<String>) {
        try {
            val likesString = likes.joinToString(",")
            val likeCount = likes.size
            
            // First get the entity to make sure it exists
            val entity = feedItemDao.getFeedItemById(postId)
            
            if (entity != null) {
                // Update both likes list and count
                feedItemDao.updateLikesWithCount(postId, likeCount, likesString)
                Log.d(TAG, "Updated likes in Room for post $postId: count=$likeCount")
            } else {
                Log.w(TAG, "Tried to update likes for non-existent post: $postId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating likes: ${e.message}", e)
        }
    }
    
    /**
     * Synchronize like data across feed and profile pages
     * This ensures consistent like state for the same post across different fragments
     */
    suspend fun syncPostLikes(postId: String) {
        try {
            // Get the latest data from Firestore
            val postSnapshot = firestore.collection("Posts").document(postId).get().await()
            
            if (postSnapshot.exists()) {
                // Extract likes from Firestore
                val likes = when (val likesValue = postSnapshot.get("likes")) {
                    is List<*> -> likesValue.filterIsInstance<String>()
                    else -> listOf()
                }
                
                // Get the correct like count
                val likeCount = likes.size
                
                // Update Room database with both likes and count
                updateLikes(postId, likes)
                
                // Double-check the item exists and has correct count
                val entity = feedItemDao.getFeedItemById(postId)
                if (entity != null && entity.likeCount != likeCount) {
                    // Force update the like count if it's different
                    feedItemDao.updateLikeCount(postId, likeCount)
                }
                
                Log.d(TAG, "Synchronized likes for post $postId from Firestore to Room: count=$likeCount")
            } else {
                Log.w(TAG, "Post $postId not found in Firestore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error synchronizing post likes: ${e.message}", e)
        }
    }

    suspend fun clearAllFeedItems() {
        try {
            feedItemDao.deleteAllFeedItems()
            Log.d(TAG, "Cleared all feed items from Room")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing feed items: ${e.message}", e)
        }
    }

    // Add a method to delete a feed item by ID
    suspend fun deleteFeedItem(postId: String) {
        try {
            feedItemDao.deleteFeedItem(postId)
            Log.d(TAG, "Deleted feed item $postId from Room")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting feed item: ${e.message}", e)
        }
    }

    // Firestore operations
    suspend fun loadInitialPosts(pageSize: Int = 6, forceRefresh: Boolean = false): List<FeedItem> {
        return try {
            Log.d(TAG, "Loading initial posts from Firestore, forceRefresh=$forceRefresh")
            
            // Use a different query approach when force refreshing
            val postsQuery = firestore.collection("Posts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())
                
            // Execute query with proper caching strategy
            val result = if (forceRefresh) {
                // When force refreshing, use get(Source.SERVER) to bypass cache
                postsQuery.get(com.google.firebase.firestore.Source.SERVER).await()
            } else {
                // Default behavior - use cache when available
                postsQuery.get().await()
            }

            val posts = result.documents.mapNotNull { doc ->
                mapDocumentToFeedItem(doc)
            }
            
            // Cache in Room if we got posts
            if (posts.isNotEmpty()) {
                // If forcing refresh, clear existing posts first to avoid stale data
                if (forceRefresh) {
                    clearAllFeedItems()
                }
                
                // Then insert the fresh posts
                insertFeedItems(posts)
                
                Log.d(TAG, "Cached ${posts.size} posts from Firestore to Room")
            } else {
                Log.d(TAG, "No posts found in Firestore")
            }
            
            posts
        } catch (e: Exception) {
            Log.e(TAG, "Error loading initial posts from Firestore: ${e.message}", e)
            
            // If Firestore query fails, try to get cached data from Room
            // But only if we're not forcing a refresh
            if (!forceRefresh) {
                try {
                    val cachedPosts = getAllFeedItems()
                    Log.d(TAG, "Falling back to ${cachedPosts.size} cached posts from Room")
                    cachedPosts
                } catch (e2: Exception) {
                    Log.e(TAG, "Error loading cached posts: ${e2.message}", e2)
                    emptyList()
                }
            } else {
                // If we were forcing a refresh, don't fall back to cache
                emptyList()
            }
        }
    }

    suspend fun loadMorePosts(lastVisible: DocumentSnapshot, pageSize: Int = 4): List<FeedItem> {
        return try {
            val result = firestore.collection("Posts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .startAfter(lastVisible)
                .limit(pageSize.toLong())
                .get()
                .await()

            val posts = result.documents.mapNotNull { doc ->
                mapDocumentToFeedItem(doc)
            }
            
            // Cache in Room
            if (posts.isNotEmpty()) {
                insertFeedItems(posts)
            }
            
            posts
        } catch (e: Exception) {
            Log.e(TAG, "Error loading more posts from Firestore: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun loadUserPosts(userId: String): List<FeedItem> = withContext(Dispatchers.IO) {
        try {
            val posts = mutableListOf<FeedItem>()
            val firestorePosts = firestore.collection("Posts")
                .whereEqualTo("UserId", userId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            Log.d("FeedRepository", "Retrieved ${firestorePosts.documents.size} posts for user $userId from Firestore")
            
            // Process the results
            for (document in firestorePosts.documents) {
                try {
                    mapDocumentToFeedItem(document)?.let { post ->
                        posts.add(post)
                        
                        // Add to Room database
                        insertFeedItem(post)
                    }
                } catch (e: Exception) {
                    Log.e("FeedRepository", "Error processing post ${document.id}: ${e.message}")
                }
            }
            
            // Now update with refreshed user data (with force refresh to ensure latest data)
            val updatedPosts = refreshUserDataInPosts(posts, forceUserRefresh = true)
            
            return@withContext updatedPosts
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("FeedRepository", "Error loading user posts: ${e.message}", e)
            return@withContext emptyList<FeedItem>()
        }
    }

    fun mapDocumentToFeedItem(doc: DocumentSnapshot): FeedItem? {
        try {
            val locationMap = doc.get("location") as? Map<String, Any>
            val location = if (locationMap != null) {
                FeedItem.Location(
                    name = locationMap["name"] as? String ?: "",
                    latitude = (locationMap["latitude"] as? Double) ?: 0.0,
                    longitude = (locationMap["longitude"] as? Double) ?: 0.0,
                    placeId = locationMap["placeId"] as? String
                )
            } else null

            // Handle likes
            val likes = when (val likesValue = doc.get("likes")) {
                is List<*> -> likesValue.filterIsInstance<String>()
                else -> listOf()
            }

            // Get userId from either field
            val userId = doc.getString("UserId") ?: doc.getString("userId") ?: ""

            return FeedItem(
                id = doc.id,
                userId = userId,
                userName = doc.getString("userName") ?: "",
                experienceDescription = doc.getString("experienceDescription") ?: "",
                location = location,
                photoUrl = doc.getString("photoUrl"),
                timestamp = doc.getLong("timestamp") ?: 0,
                userPhotoUrl = doc.getString("userPhotoUrl"),
                likeCount = (doc.getLong("likeCount") ?: 0).toInt(),
                commentCount = (doc.getLong("commentCount") ?: 0).toInt(),
                likes = likes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping document to FeedItem: ${e.message}", e)
            return null
        }
    }

    // Add method to update user data in all posts by a specific user
    suspend fun updateUserDataInPosts(userId: String, userName: String, profilePhotoUrl: String?) {
        try {
            // First update local Room database
            val localPosts = feedItemDao.getFeedItemsByUserId(userId)
            if (localPosts.isNotEmpty()) {
                Log.d(TAG, "Updating user data for ${localPosts.size} posts in Room for user $userId")
                
                // Update each post by creating a new entity with the updated data
                for (post in localPosts) {
                    try {
                        // Create a new entity with updated user data
                        val updatedPost = FeedItemEntity(
                            id = post.id,
                            userId = post.userId,
                            userName = userName, // Update with new name
                            experienceDescription = post.experienceDescription,
                            timestamp = post.timestamp,
                            likeCount = post.likeCount,
                            commentCount = post.commentCount,
                            likes = post.likes,
                            photoUrl = post.photoUrl,
                            userPhotoUrl = profilePhotoUrl, // Update with new photo URL
                            locationName = post.locationName,
                            locationLatitude = post.locationLatitude,
                            locationLongitude = post.locationLongitude,
                            locationPlaceId = post.locationPlaceId
                        )
                        
                        // Update in database
                        feedItemDao.updateFeedItem(updatedPost)
                    } catch (e: Exception) {
                        // Handle individual post update errors
                        if (e is kotlinx.coroutines.CancellationException) {
                            Log.w(TAG, "Job was cancelled while updating posts, partial update may have occurred")
                            break // Exit the loop if job is cancelled
                        } else {
                            Log.e(TAG, "Error updating post ${post.id}: ${e.message}")
                            // Continue with other posts
                        }
                    }
                }
                
                // Clear Picasso cache for this user's photo URL if available
                if (profilePhotoUrl != null) {
                    try {
                        // This will invalidate the specific URL in Picasso's cache
                        com.squareup.picasso.Picasso.get().invalidate(profilePhotoUrl)
                        Log.d(TAG, "Invalidated Picasso cache for user photo: $profilePhotoUrl")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error invalidating Picasso cache: ${e.message}")
                    }
                }
            }
            
            // Don't need to update Firestore since we don't store redundant user data there
            // When posts are loaded, we'll fetch fresh user data anyway
            
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                Log.w(TAG, "Job was cancelled while updating posts, partial update may have occurred")
            } else {
                Log.e(TAG, "Error updating user data in posts: ${e.message}", e)
            }
        }
    }

    /**
     * Updates user data in a list of posts using Room database data
     * @param posts The list of posts to update
     * @param forceUserRefresh Whether to force refresh user data from Firebase
     * @return Updated posts with fresh user data
     */
    suspend fun refreshUserDataInPosts(posts: List<FeedItem>, forceUserRefresh: Boolean = false): List<FeedItem> = withContext(Dispatchers.IO) {
        try {
            if (posts.isEmpty()) return@withContext emptyList<FeedItem>()
            
            // Get unique user IDs from the posts
            val userIds = posts.map { it.userId }.distinct()
            
            // If forcing refresh, get users directly from Firebase
            val users = if (forceUserRefresh) {
                // This will force refresh from Firestore for all users
                Log.d("FeedRepository", "Force refreshing user data for ${userIds.size} users")
                userRepository.getUsersMapByIds(userIds, forceRefresh = true).values.toList()
            } else {
                // Get from local cache
                userRepository.getUsersByIds(userIds)
            }
            
            // Create a map of userId to user data for quick lookup
            val userMap = users.associateBy { it.uid }
            
            // Track if we made changes to any posts
            var updatesMade = false
            
            // Update posts with the latest user data
            val updatedPosts = posts.map { post ->
                val user = userMap[post.userId]
                if (user != null) {
                    // Only create a new object if data is different
                    if (post.userName != user.name || post.userPhotoUrl != user.profilePhotoUrl) {
                        updatesMade = true
                        
                        // Invalidate image cache for profile photo if it changed
                        if (post.userPhotoUrl != user.profilePhotoUrl && !user.profilePhotoUrl.isNullOrEmpty()) {
                            try {
                                com.squareup.picasso.Picasso.get().invalidate(user.profilePhotoUrl)
                                Log.d("FeedRepository", "Invalidated cache for user photo: ${user.profilePhotoUrl?.take(20)}")
                            } catch (e: Exception) {
                                Log.e("FeedRepository", "Error invalidating image cache: ${e.message}")
                            }
                        }
                        
                        post.copy(
                            userName = user.name,
                            userPhotoUrl = user.profilePhotoUrl
                        )
                    } else {
                        post // Return unchanged post if no user data changed
                    }
                } else {
                    post // Return unchanged post if user not found
                }
            }
            
            if (updatesMade) {
                Log.d("FeedRepository", "Updated user data in ${updatedPosts.size} posts")
            } else {
                Log.d("FeedRepository", "No user data changes needed in posts")
            }
            
            return@withContext updatedPosts
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("FeedRepository", "Error refreshing user data in posts: ${e.message}")
            return@withContext posts // Return original posts on error
        }
    }

    /**
     * Clear all feed items from a specific user
     */
    suspend fun clearUserFeedItems(userId: String) {
        try {
            feedItemDao.deleteUserFeedItems(userId)
            Log.d(TAG, "Cleared feed items for user $userId from Room")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing feed items for user $userId: ${e.message}", e)
        }
    }

    /**
     * Updates all feed items in the database that belong to a specific user with new user data.
     * This ensures consistent display across the app when user info changes.
     */
    suspend fun refreshUserDataInAllPosts(userId: String, forceRefresh: Boolean = false) {
        Log.d(TAG, "Refreshing user data in all posts for user: $userId")
        
        try {
            // First, get the latest user data from UserRepository
            val userData = userRepository.getUserData(userId, forceRefresh = forceRefresh)
            
            if (userData != null) {
                Log.d(TAG, "Got latest user data: ${userData.name}")
                
                // Update user data in both Room database and Firestore
                updateUserDataInPostsInternal(
                    userId = userId,
                    userName = userData.name,
                    profilePhotoUrl = userData.profilePhotoUrl
                )
                
                // Invalidate any cached images for this user
                userData.profilePhotoUrl?.let { url ->
                    if (url.isNotEmpty()) {
                        try {
                            com.squareup.picasso.Picasso.get().invalidate(url)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error invalidating Picasso cache: ${e.message}")
                        }
                    }
                }
                
                // Set global refresh flags
                GlobalState.shouldRefreshFeed = true
                GlobalState.shouldRefreshProfile = true
            } else {
                Log.e(TAG, "Failed to fetch user data for refresh")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing user data in posts: ${e.message}")
            throw e
        }
    }
    
    /**
     * Updates user data in all posts by a specific user
     * This updates both the local Room database and sends a Firestore update for each post
     */
    private suspend fun updateUserDataInPostsInternal(userId: String, userName: String, profilePhotoUrl: String?) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating user data in posts for user $userId to name=$userName, photoUrl=$profilePhotoUrl")
            
            // Step 1: Update in Room database
            val userPosts = feedItemDao.getFeedItemsByUserId(userId)
            var updatedCount = 0
            
            for (postEntity in userPosts) {
                // Check if data actually needs updating
                if (postEntity.userName != userName || postEntity.userPhotoUrl != profilePhotoUrl) {
                    // Create updated entity
                    val updatedEntity = postEntity.copy(
                        userName = userName,
                        userPhotoUrl = profilePhotoUrl
                    )
                    
                    // Insert (replace) in Room
                    feedItemDao.insertFeedItem(updatedEntity)
                    updatedCount++
                }
            }
            
            Log.d(TAG, "Updated $updatedCount posts in Room database")
            
            // Step 2: Update in Firestore
            // Only do batch updates in chunks to avoid exceeding Firestore limits
            val MAX_BATCH_SIZE = 500
            
            try {
                val postIds = userPosts.map { it.id }
                for (i in postIds.indices step MAX_BATCH_SIZE) {
                    val batch = firestore.batch()
                    val endIndex = minOf(i + MAX_BATCH_SIZE, postIds.size)
                    var batchCount = 0
                    
                    for (j in i until endIndex) {
                        val postId = postIds[j]
                        val postRef = firestore.collection("Posts").document(postId)
                        
                        // Update user info in Firestore
                        val updates = hashMapOf<String, Any?>()
                        updates["userName"] = userName
                        updates["userPhotoUrl"] = profilePhotoUrl
                        
                        batch.update(postRef, updates)
                        batchCount++
                    }
                    
                    // Commit the batch
                    if (batchCount > 0) {
                        batch.commit().await()
                        Log.d(TAG, "Updated batch of $batchCount posts in Firestore")
                    }
                }
                
                Log.d(TAG, "Completed Firestore updates for user $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating posts in Firestore: ${e.message}")
                // Continue execution even if Firestore update fails
            }
            
            // Return the number of updated posts
            return@withContext updatedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user data in posts: ${e.message}")
            return@withContext 0
        }
    }

    /**
     * Cache a list of feed items in the Room database with optimized handling
     * This ensures all posts have the most up-to-date user data before caching
     */
    suspend fun cacheFeedItems(posts: List<FeedItem>) = withContext(Dispatchers.IO) {
        if (posts.isEmpty()) return@withContext
        
        try {
            Log.d(TAG, "Caching ${posts.size} feed items to Room")
            
            // First ensure all posts have up-to-date user data
            val userIds = posts.map { it.userId }.distinct()
            
            // No need to force refresh here as we'll get cached user data if available
            val usersMap = userRepository.getUsersMapByIds(userIds)
            Log.d(TAG, "Got ${usersMap.size} users for caching")
            
            // Make a copy of posts with updated user data to ensure consistency
            val postsToCache = posts.map { post ->
                val user = usersMap[post.userId]
                
                // Update user data if available and different from current values
                if (user != null && !user.name.isNullOrEmpty() && 
                    (post.userName != user.name || post.userPhotoUrl != user.profilePhotoUrl)) {
                    
                    // Create a new post with updated user data
                    post.copy(
                        userName = user.name,
                        userPhotoUrl = user.profilePhotoUrl
                    )
                } else {
                    // Use original post if no user data to update
                    post
                }
            }
            
            // Convert posts to entities and insert into Room
            val entities = postsToCache.map { FeedItemEntity.fromFeedItem(it) }
            feedItemDao.insertFeedItems(entities)
            
            Log.d(TAG, "Successfully cached ${entities.size} feed items with up-to-date user data")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching feed items: ${e.message}", e)
        }
    }
} 