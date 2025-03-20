package com.eaor.coffeefee.models

data class CoffeeShop(
    val name: String,
    val rating: Float?,  // Rating from 0 to 5, null if no rating
    val caption: String,
    val latitude: Double,
    val longitude: Double,
    var address: String? = null,
    var photoUrl: String? = null,  // URL of the main photo from Google Places
    var placeId: String? = null,   // Google Places ID for future reference
    var tags: List<String> = emptyList() // Tags for categorizing and matching with user preferences
) 