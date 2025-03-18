package com.eaor.coffeefee.models

import com.google.firebase.auth.FirebaseAuth
import com.google.gson.annotations.SerializedName

data class FeedItem(
    var id: String = "",
    @field:SerializedName("UserId")
    val userId: String = "",
    val userName: String = "",
    val experienceDescription: String = "",
    val location: Location? = null,
    val photoUrl: String? = null,
    val timestamp: Long = 0,
    var userPhotoUrl: String? = null,
    var likeCount: Int = 0,
    var commentCount: Int = 0,
    var likes: List<String> = listOf(),
    var isLikedByCurrentUser: Boolean = false
) {
    // No-argument constructor for Firestore deserialization
    constructor() : this("", "", "", "", null, null, 0, null, 0, 0, listOf(), false)

    init {
        // Set isLikedByCurrentUser based on the likes list
        updateLikeState()
    }

    private fun updateLikeState() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        isLikedByCurrentUser = currentUserId != null && likes.contains(currentUserId)
    }

    // Add a method to update likes and refresh the like state
    fun updateLikes(newLikes: List<String>) {
        likes = newLikes
        updateLikeState()
    }

    data class Location(
        val name: String = "",           // Default value for name
        val latitude: Double = 0.0,      // Default value for latitude
        val longitude: Double = 0.0,
        val placeId: String? = null       // Optional parameter at the end
    ) {
        // No-argument constructor for Firestore deserialization
        constructor() : this("", 0.0, 0.0, null)
        // Add secondary constructor for backward compatibility
        constructor(name: String, latitude: Double, longitude: Double) : this(
            name = name,
            latitude = latitude,
            longitude = longitude,
            placeId = null
        )
    }

    // Convert to HashMap for Firestore
    fun toMap(): Map<String, Any?> {
        return hashMapOf(
            "id" to id,
            "userId" to userId,
            "userName" to userName,
            "experienceDescription" to experienceDescription,
            "location" to location?.let {
                mapOf(
                    "name" to it.name,
                    "latitude" to it.latitude,
                    "longitude" to it.longitude,
                    "placeId" to it.placeId
                )
            },
            "photoUrl" to photoUrl,
            "timestamp" to timestamp,
            "userPhotoUrl" to userPhotoUrl,
            "likeCount" to likeCount,
            "commentCount" to commentCount,
            "likes" to likes
        )
    }
}
