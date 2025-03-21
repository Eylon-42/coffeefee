package com.eaor.coffeefee.data

import androidx.room.*

@Dao
interface FeedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedItem(feedItem: FeedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedItems(feedItems: List<FeedItemEntity>)

    @Query("SELECT * FROM feed_items ORDER BY timestamp DESC")
    suspend fun getAllFeedItems(): List<FeedItemEntity>

    @Query("SELECT * FROM feed_items WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getFeedItemsByUserId(userId: String): List<FeedItemEntity>

    @Query("SELECT * FROM feed_items WHERE id = :id")
    suspend fun getFeedItemById(id: String): FeedItemEntity?

    @Query("DELETE FROM feed_items")
    suspend fun deleteAllFeedItems()

    @Query("DELETE FROM feed_items WHERE id = :id")
    suspend fun deleteFeedItem(id: String)

    @Update
    suspend fun updateFeedItem(feedItem: FeedItemEntity)

    @Query("UPDATE feed_items SET commentCount = :count WHERE id = :postId")
    suspend fun updateCommentCount(postId: String, count: Int)
} 