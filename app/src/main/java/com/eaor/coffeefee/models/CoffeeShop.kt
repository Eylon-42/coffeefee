package com.eaor.coffeefee.models

data class CoffeeShop(
    val name: String,
    val rating: Float,  // Rating from 0 to 5
    val caption: String,
    val latitude: Double,
    val longitude: Double
) 