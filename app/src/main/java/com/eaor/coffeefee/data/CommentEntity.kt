package com.eaor.coffeefee.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eaor.coffeefee.models.Comment

/**
 * Room entity representing a comment in the local database
 * Comments are linked to feed items and users
 */
@Entity(
    tableName = "comments",
    indices = [
        Index("postId"), // Index to speed up queries by postId
        Index("userId")  // Index to speed up queries by userId
    ]
)
data class CommentEntity(
    @PrimaryKey
    val id: String,
    val postId: String,
    val userId: String,
    val text: String,
    val timestamp: Long
) {
    /**
     * Convert Entity to Domain model
     */
    fun toComment(userName: String = "", userPhotoUrl: String? = null): Comment {
        return Comment(
            id = id,
            postId = postId,
            userId = userId,
            text = text,
            timestamp = timestamp,
            userName = userName,
            userPhotoUrl = userPhotoUrl
        )
    }

    companion object {
        /**
         * Convert Domain model to Entity
         */
        fun fromComment(comment: Comment): CommentEntity {
            return CommentEntity(
                id = comment.id,
                postId = comment.postId,
                userId = comment.userId,
                text = comment.text,
                timestamp = comment.timestamp
            )
        }
    }
} 