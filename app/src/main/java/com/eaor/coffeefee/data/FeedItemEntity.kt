package com.eaor.coffeefee.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.eaor.coffeefee.models.FeedItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "feed_items")
data class FeedItemEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val userName: String,
    val experienceDescription: String,
    val timestamp: Long,
    val likeCount: Int,
    val commentCount: Int,
    val likes: String, // Stored as comma-separated values
    val photoUrl: String?,
    val userPhotoUrl: String?,
    val locationName: String?,
    val locationLatitude: Double?,
    val locationLongitude: Double?,
    val locationPlaceId: String?
) {
    // Convert Entity to Model
    fun toFeedItem(): FeedItem {
        val location = if (locationName != null) {
            FeedItem.Location(
                name = locationName,
                latitude = locationLatitude ?: 0.0,
                longitude = locationLongitude ?: 0.0,
                placeId = locationPlaceId
            )
        } else null

        return FeedItem(
            id = id,
            userId = userId,
            userName = userName,
            experienceDescription = experienceDescription,
            location = location,
            photoUrl = photoUrl,
            timestamp = timestamp,
            userPhotoUrl = userPhotoUrl,
            likeCount = likeCount,
            commentCount = commentCount,
            likes = likes.split(",").filter { it.isNotEmpty() }
        )
    }

    companion object {
        // Convert Model to Entity
        fun fromFeedItem(feedItem: FeedItem): FeedItemEntity {
            return FeedItemEntity(
                id = feedItem.id,
                userId = feedItem.userId,
                userName = feedItem.userName,
                experienceDescription = feedItem.experienceDescription,
                timestamp = feedItem.timestamp,
                likeCount = feedItem.likeCount,
                commentCount = feedItem.commentCount,
                likes = feedItem.likes.joinToString(","),
                photoUrl = feedItem.photoUrl,
                userPhotoUrl = feedItem.userPhotoUrl,
                locationName = feedItem.location?.name,
                locationLatitude = feedItem.location?.latitude,
                locationLongitude = feedItem.location?.longitude,
                locationPlaceId = feedItem.location?.placeId
            )
        }
    }
} 