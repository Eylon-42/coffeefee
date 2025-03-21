package com.eaor.coffeefee.models

/**
 * Represents a coffee entity in the application
 */
data class Coffee(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String,
    val categoryId: String,
    val categoryName: String,
    val price: Double,
    val rating: Double,
    val shopId: String,
    val isFeatured: Boolean
) 