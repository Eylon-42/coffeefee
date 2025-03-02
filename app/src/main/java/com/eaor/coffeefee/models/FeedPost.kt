package com.eaor.coffeefee.models

data class FeedItem(
    val id: String,
    val userName: String,
    val userDescription: String,
    val coffeeShop: CoffeeShop,
    val reviewText: String
)