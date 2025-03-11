package com.eaor.coffeefee.models

data class FeedItem(
    val id: String,                 // Unique identifier for the post
    val userId: String,             // User ID
    val userName: String,           // User's name
    val experienceDescription: String, // User's experience description
    val location: Location,         // Location object containing details
    val photoUrl: String?,          // URL to the photo associated with the post
    val timestamp: Long             // Timestamp (e.g., the time the post was created)
) {
    data class Location(
        val name: String,           // Name of the place (e.g., cafe name)
        val latitude: Double,       // Latitude of the location
        val longitude: Double      // Longitude of the location
    )
}
