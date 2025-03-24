package com.eaor.coffeefee.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * Data Access Object for Comments table
 * Provides methods to access and manipulate comment data in Room database
 */
@Dao
interface CommentDao {
    /**
     * Get all comments for a specific post, ordered by timestamp
     * Uses LiveData for automatic UI updates
     */
    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY timestamp DESC")
    fun getCommentsForPostLive(postId: String): LiveData<List<CommentEntity>>
    
    /**
     * Get all comments for a specific post, ordered by timestamp (non-LiveData version)
     */
    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY timestamp DESC")
    suspend fun getCommentsForPost(postId: String): List<CommentEntity>
    
    /**
     * Get a specific comment by ID
     */
    @Query("SELECT * FROM comments WHERE id = :commentId")
    suspend fun getCommentById(commentId: String): CommentEntity?
    
    /**
     * Get count of comments for a specific post
     */
    @Query("SELECT COUNT(*) FROM comments WHERE postId = :postId")
    fun getCommentCountForPost(postId: String): LiveData<Int>
    
    /**
     * Get count of comments for a specific post (non-LiveData version)
     */
    @Query("SELECT COUNT(*) FROM comments WHERE postId = :postId")
    suspend fun getCommentCountForPostSync(postId: String): Int
    
    /**
     * Get comments by a specific user
     */
    @Query("SELECT * FROM comments WHERE userId = :userId ORDER BY timestamp DESC")
    fun getCommentsByUserLive(userId: String): LiveData<List<CommentEntity>>
    
    /**
     * Insert a single comment
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)
    
    /**
     * Insert multiple comments
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)
    
    /**
     * Update an existing comment
     */
    @Update
    suspend fun updateComment(comment: CommentEntity)
    
    /**
     * Delete a specific comment
     */
    @Query("DELETE FROM comments WHERE id = :commentId")
    suspend fun deleteComment(commentId: String)
    
    /**
     * Delete all comments for a specific post
     */
    @Query("DELETE FROM comments WHERE postId = :postId")
    suspend fun deleteCommentsForPost(postId: String)
    
    /**
     * Delete all comments
     */
    @Query("DELETE FROM comments")
    suspend fun deleteAllComments()
    
    /**
     * Update the comment count for a specific post directly
     * This is for keeping the post's comment count in sync with the actual comments
     */
    @Query("UPDATE feed_items SET commentCount = :count WHERE id = :postId")
    suspend fun updateCommentCountForPost(postId: String, count: Int)
} 