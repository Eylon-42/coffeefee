package com.eaor.coffeefee.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eaor.coffeefee.models.CoffeeShop

@Entity(tableName = "coffee_shops")
data class CoffeeShopEntity(
    @PrimaryKey
    val placeId: String,
    val name: String,
    val rating: Float?,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val photoUrl: String?,
    val tags: List<String> = emptyList()
) {
    // Convert Entity to Model
    fun toCoffeeShop(): CoffeeShop {
        return CoffeeShop(
            name = name,
            rating = rating,
            description = description,
            latitude = latitude,
            longitude = longitude,
            address = address,
            photoUrl = photoUrl,
            placeId = placeId,
            tags = tags
        )
    }

    companion object {
        // Convert Model to Entity
        fun fromCoffeeShop(coffeeShop: CoffeeShop): CoffeeShopEntity {
            return CoffeeShopEntity(
                placeId = coffeeShop.placeId ?: "",
                name = coffeeShop.name,
                rating = coffeeShop.rating,
                description = coffeeShop.description,
                latitude = coffeeShop.latitude,
                longitude = coffeeShop.longitude,
                address = coffeeShop.address,
                photoUrl = coffeeShop.photoUrl,
                tags = coffeeShop.tags
            )
        }
    }
} 