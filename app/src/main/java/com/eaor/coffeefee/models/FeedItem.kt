// Remove the comment markers and restore the FeedItem class
package com.eaor.coffeefee.models

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.PropertyName
import com.google.gson.annotations.SerializedName

data class FeedItem(
    var id: String = "",
    @PropertyName("UserId")
    val userId: String = "",
    var userName: String = "",
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
        // Ensure likeCount matches the actual number of likes
        likeCount = likes.size
    }

    // Add method to check if the current user has liked this post
    fun hasUserLiked(userId: String?): Boolean {
        return userId != null && likes.contains(userId)
    }

    // Add a method to toggle like for a specific user
    fun toggleLike(userId: String?): Boolean {
        if (userId == null) return false
        
        val wasLiked = likes.contains(userId)
        likes = if (wasLiked) {
            // Remove the like
            likes.filter { it != userId }
        } else {
            // Add the like if not already present
            if (!likes.contains(userId)) likes + userId else likes
        }
        
        // Update like count to match the size of likes list
        likeCount = likes.size
        
        // Update the isLikedByCurrentUser flag
        updateLikeState()
        
        // Return the new like state
        return !wasLiked
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
            "UserId" to userId,  // Use uppercase in Firestore
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
