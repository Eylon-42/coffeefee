package com.eaor.coffeefee.repositories

import android.util.Log
import com.eaor.coffeefee.models.CoffeeShop
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CoffeeShopRepository {
    private val db = FirebaseFirestore.getInstance()
    private val coffeeShopsCollection = db.collection("CoffeeShops")

    suspend fun getCoffeeShop(placeId: String): CoffeeShop? {
        return try {
            val document = coffeeShopsCollection.document(placeId).get().await()
            if (document.exists()) {
                val data = document.data
                CoffeeShop(
                    name = data?.get("name") as? String ?: "",
                    rating = (data?.get("rating") as? Double)?.toFloat() ?: 0f,
                    caption = data?.get("caption") as? String ?: "",
                    latitude = (data?.get("latitude") as? Double) ?: 0.0,
                    longitude = (data?.get("longitude") as? Double) ?: 0.0,
                    placeId = placeId,
                    photoUrl = data?.get("photoUrl") as? String,
                    address = data?.get("address") as? String
                )
            } else null
        } catch (e: Exception) {
            Log.e("CoffeeShopRepository", "Error getting coffee shop", e)
            null
        }
    }

    fun getAllCoffeeShops(): Flow<List<CoffeeShop>> = flow {
        try {
            val snapshot = coffeeShopsCollection.get().await()
            val coffeeShops = snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                CoffeeShop(
                    name = data["name"] as? String ?: "",
                    rating = (data["rating"] as? Double)?.toFloat() ?: 0f,
                    caption = data["caption"] as? String ?: "",
                    latitude = data["latitude"] as? Double ?: 0.0,
                    longitude = data["longitude"] as? Double ?: 0.0,
                    placeId = document.id,
                    photoUrl = data["photoUrl"] as? String,
                    address = data["address"] as? String
                )
            }
            emit(coffeeShops)
        } catch (e: Exception) {
            Log.e("CoffeeShopRepository", "Error getting all coffee shops", e)
            emit(emptyList())
        }
    }

    suspend fun searchCoffeeShops(query: String): List<CoffeeShop> {
        return try {
            val snapshot = coffeeShopsCollection
                .orderBy("name")
                .whereGreaterThanOrEqualTo("name", query)
                .whereLessThanOrEqualTo("name", query + '\uf8ff')
                .get()
                .await()
            
            snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                CoffeeShop(
                    name = data["name"] as? String ?: "",
                    rating = (data["rating"] as? Double)?.toFloat() ?: 0f,
                    caption = data["caption"] as? String ?: "",
                    latitude = data["latitude"] as? Double ?: 0.0,
                    longitude = data["longitude"] as? Double ?: 0.0,
                    placeId = document.id,
                    photoUrl = data["photoUrl"] as? String,
                    address = data["address"] as? String
                )
            }
        } catch (e: Exception) {
            Log.e("CoffeeShopRepository", "Error searching coffee shops", e)
            emptyList()
        }
    }

    suspend fun getNearbyShops(latitude: Double, longitude: Double, radiusKm: Double = 10.0): List<CoffeeShop> {
        return try {
            val allShops = coffeeShopsCollection.get().await()
            allShops.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                val shopLat = data["latitude"] as? Double ?: return@mapNotNull null
                val shopLng = data["longitude"] as? Double ?: return@mapNotNull null
                
                val distance = calculateDistance(
                    latitude, longitude,
                    shopLat, shopLng
                )
                
                if (distance <= radiusKm) {
                    CoffeeShop(
                        name = data["name"] as? String ?: "",
                        rating = (data["rating"] as? Double)?.toFloat() ?: 0f,
                        caption = data["caption"] as? String ?: "",
                        latitude = shopLat,
                        longitude = shopLng,
                        placeId = document.id,
                        photoUrl = data["photoUrl"] as? String,
                        address = data["address"] as? String
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e("CoffeeShopRepository", "Error getting nearby shops", e)
            emptyList()
        }
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371.0 // Earth's radius in kilometers
        
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return r * c
    }

    companion object {
        @Volatile
        private var instance: CoffeeShopRepository? = null

        fun getInstance(): CoffeeShopRepository {
            return instance ?: synchronized(this) {
                instance ?: CoffeeShopRepository().also { instance = it }
            }
        }
    }
} 