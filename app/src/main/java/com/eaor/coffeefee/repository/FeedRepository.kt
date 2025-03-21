package com.eaor.coffeefee.repository

import android.util.Log
import com.eaor.coffeefee.data.FeedItemDao
import com.eaor.coffeefee.data.FeedItemEntity
import com.eaor.coffeefee.models.FeedItem
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FeedRepository(
    // Make the DAO public so it can be accessed by the ViewModel
    val feedItemDao: FeedItemDao,
    private val firestore: FirebaseFirestore
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
    suspend fun loadInitialPosts(pageSize: Int = 6): List<FeedItem> {
        return try {
            Log.d(TAG, "Loading initial posts from Firestore")
            val result = firestore.collection("Posts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
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
            Log.e(TAG, "Error loading initial posts from Firestore: ${e.message}", e)
            emptyList()
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

    suspend fun loadUserPosts(userId: String): List<FeedItem> {
        return try {
            // First try with uppercase "UserId"
            var result = firestore.collection("Posts")
                .whereEqualTo("UserId", userId)
                .get()
                .await()

            if (result.isEmpty) {
                // If no results, try with lowercase "userId"
                result = firestore.collection("Posts")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
            }

            val posts = result.documents.mapNotNull { doc ->
                mapDocumentToFeedItem(doc)
            }
            
            // Sort by timestamp descending
            val sortedPosts = posts.sortedByDescending { it.timestamp }
            
            // Cache in Room
            if (sortedPosts.isNotEmpty()) {
                insertFeedItems(sortedPosts)
            }
            
            sortedPosts
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user posts from Firestore: ${e.message}", e)
            emptyList()
        }
    }

    private fun mapDocumentToFeedItem(doc: DocumentSnapshot): FeedItem? {
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
                localPosts.forEach { post ->
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
                }
            }
            
            // Don't need to update Firestore since we don't store redundant user data there
            // When posts are loaded, we'll fetch fresh user data anyway
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user data in posts: ${e.message}", e)
        }
    }
} 