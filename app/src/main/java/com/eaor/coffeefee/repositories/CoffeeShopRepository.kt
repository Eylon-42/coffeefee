package com.eaor.coffeefee.repositories

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.data.CoffeeShopDao
import com.eaor.coffeefee.data.CoffeeShopEntity
import com.eaor.coffeefee.models.CoffeeShop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.io.ByteArrayOutputStream

class CoffeeShopRepository private constructor(context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val coffeeShopsCollection = db.collection("CoffeeShops")
    private val coffeeShopDao: CoffeeShopDao = AppDatabase.getDatabase(context).coffeeShopDao()
    private val TAG = "CoffeeShopRepository"

    // Store the last refresh time
    private var lastCacheRefreshTime = 0L
    
    // Cache time-to-live in milliseconds (30 minutes)
    private val CACHE_TTL = 30 * 60 * 1000L
    
    // Check if the cache is stale and needs refreshing
    private fun isCacheStale(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastCacheRefreshTime > CACHE_TTL
    }

    suspend fun getCoffeeShop(placeId: String, forceRefresh: Boolean = false): CoffeeShop? {
        return try {
            // First try to get from local database
            val cachedShop = withContext(Dispatchers.IO) {
                coffeeShopDao.getCoffeeShopById(placeId)?.toCoffeeShop()
            }
            
            // Refresh from Firestore if forced, cache miss, or cache is stale
            if (forceRefresh || cachedShop == null || isCacheStale()) {
                val stateReason = when {
                    forceRefresh -> "force refreshed"
                    cachedShop == null -> "not in cache"
                    else -> "cache is stale"
                }
                Log.d(TAG, "Fetching coffee shop $placeId from Firestore ($stateReason)")
                
                val document = coffeeShopsCollection.document(placeId).get().await()
                if (document.exists()) {
                    val data = document.data
                    val coffeeShop = CoffeeShop(
                        name = data?.get("name") as? String ?: "",
                        rating = (data?.get("rating") as? Double)?.toFloat() ?: 0f,
                        description = data?.get("description") as? String ?: "",
                        latitude = (data?.get("latitude") as? Double) ?: 0.0,
                        longitude = (data?.get("longitude") as? Double) ?: 0.0,
                        placeId = placeId,
                        photoUrl = data?.get("photoUrl") as? String,
                        address = data?.get("address") as? String,
                        tags = data?.get("tags") as? List<String> ?: emptyList()
                    )
                    
                    // Update the cache with fresh data
                    withContext(Dispatchers.IO) {
                        coffeeShopDao.insertCoffeeShop(CoffeeShopEntity.fromCoffeeShop(coffeeShop))
                        Log.d(TAG, "Updated cache with fresh data for coffee shop $placeId")
                    }
                    
                    coffeeShop
                } else null
            } else {
                // Use cached shop
                Log.d(TAG, "Using cached coffee shop $placeId (still fresh)")
                cachedShop
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting coffee shop", e)
            
            // Try to return cached data as fallback if there was an error
            try {
                val cachedShop = withContext(Dispatchers.IO) {
                    coffeeShopDao.getCoffeeShopById(placeId)?.toCoffeeShop()
                }
                
                if (cachedShop != null) {
                    Log.d(TAG, "Returning cached coffee shop as fallback after error")
                    return cachedShop
                }
            } catch (cacheError: Exception) {
                Log.e(TAG, "Error getting from cache: ${cacheError.message}")
            }
            
            null
        }
    }

    suspend fun getAllCoffeeShops(forceRefresh: Boolean = false): List<CoffeeShop> {
        return try {
            // First check if we have data in the cache
            val cachedShops = withContext(Dispatchers.IO) {
                coffeeShopDao.getAllCoffeeShops().map { it.toCoffeeShop() }
            }
            
            // Only refresh from Firestore if forced, cache is empty, or cache is stale
            if (forceRefresh || cachedShops.isEmpty() || isCacheStale()) {
                Log.d(TAG, "Cache is ${if(cachedShops.isEmpty()) "empty" else if(forceRefresh) "force refreshed" else "stale"}, fetching from Firestore")
                
                // Fetch from Firestore
                val snapshot = coffeeShopsCollection.get().await()
                val coffeeShops = snapshot.documents.mapNotNull { document ->
                    try {
                        CoffeeShop(
                            name = document.getString("name") ?: "",
                            rating = document.getDouble("rating")?.toFloat(),
                            description = document.getString("description") ?: "",
                            latitude = document.getDouble("latitude") ?: 0.0,
                            longitude = document.getDouble("longitude") ?: 0.0,
                            address = document.getString("address"),
                            photoUrl = document.getString("photoUrl"),
                            placeId = document.id,
                            tags = document.get("tags") as? List<String> ?: emptyList()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error mapping document: ${document.id}", e)
                        null
                    }
                }
                
                // Update the cache with fresh data
                if (coffeeShops.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        // Update existing entries rather than clearing the cache completely
                        val entities = coffeeShops.map { CoffeeShopEntity.fromCoffeeShop(it) }
                        coffeeShopDao.insertCoffeeShops(entities)
                        Log.d(TAG, "Updated cache with ${entities.size} fresh coffee shops from Firestore")
                    }
                    
                    // Update the last refresh time
                    lastCacheRefreshTime = System.currentTimeMillis()
                }
                
                coffeeShops
            } else {
                // Use cached data
                Log.d(TAG, "Using ${cachedShops.size} coffee shops from Room cache (still fresh)")
                cachedShops
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting coffee shops", e)
            
            // If there was an error, try to return cached data as fallback
            try {
                val cachedShops = withContext(Dispatchers.IO) {
                    coffeeShopDao.getAllCoffeeShops().map { it.toCoffeeShop() }
                }
                
                if (cachedShops.isNotEmpty()) {
                    Log.d(TAG, "Returning ${cachedShops.size} coffee shops from cache as fallback after error")
                    return cachedShops
                }
            } catch (cacheError: Exception) {
                Log.e(TAG, "Error getting from cache: ${cacheError.message}")
            }
            
            emptyList()
        }
    }

    suspend fun searchCoffeeShops(query: String, forceRefresh: Boolean = false): List<CoffeeShop> {
        return try {
            // First try to get from local database if cache is not stale and not force refreshing
            if (!forceRefresh && !isCacheStale()) {
                val cachedResults = withContext(Dispatchers.IO) {
                    coffeeShopDao.searchCoffeeShops("%$query%").map { it.toCoffeeShop() }
                }
                
                if (cachedResults.isNotEmpty()) {
                    Log.d(TAG, "Using ${cachedResults.size} cached results for query '$query' (still fresh)")
                    return cachedResults
                }
            }
            
            // Otherwise fetch all coffee shops and filter (this will update the cache)
            Log.d(TAG, "Searching Firestore for coffee shops matching '$query'")
            val allShops = getAllCoffeeShops(forceRefresh)
            allShops.filter { shop -> 
                shop.name.contains(query, ignoreCase = true) || 
                (shop.description != null && shop.description.contains(query, ignoreCase = true)) ||
                (shop.address?.contains(query, ignoreCase = true) == true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching coffee shops: ${e.message}")
            
            // Try to return cached results as fallback
            try {
                val cachedResults = withContext(Dispatchers.IO) {
                    coffeeShopDao.searchCoffeeShops("%$query%").map { it.toCoffeeShop() }
                }
                Log.d(TAG, "Returning ${cachedResults.size} cached results as fallback after error")
                cachedResults
            } catch (cacheError: Exception) {
                Log.e(TAG, "Error getting search results from cache: ${cacheError.message}")
                emptyList()
            }
        }
    }

    suspend fun getNearbyShops(latitude: Double, longitude: Double, radiusKm: Double = 10.0): List<CoffeeShop> {
        return try {
            // First, try to get all shops from cache
            val allCachedShops = withContext(Dispatchers.IO) {
                coffeeShopDao.getAllCoffeeShops().map { it.toCoffeeShop() }
            }
            
            if (allCachedShops.isNotEmpty()) {
                // Filter by distance
                val nearbyShops = allCachedShops.filter { shop ->
                    val distance = calculateDistance(
                        latitude, longitude,
                        shop.latitude, shop.longitude
                    )
                    distance <= radiusKm
                }
                
                if (nearbyShops.isNotEmpty()) {
                    Log.d(TAG, "Found ${nearbyShops.size} nearby shops in Room cache")
                    return nearbyShops
                }
            }
            
            // If no results in cache, fetch from Firestore
            val allShops = coffeeShopsCollection.get().await()
            val nearbyShops = allShops.documents.mapNotNull { document ->
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
                        description = data["description"] as? String ?: "",
                        latitude = shopLat,
                        longitude = shopLng,
                        placeId = document.id,
                        photoUrl = data["photoUrl"] as? String,
                        address = data["address"] as? String,
                        tags = data["tags"] as? List<String> ?: emptyList()
                    )
                } else null
            }
            
            // Cache ALL shops for future queries
            if (allShops.documents.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    val entities = allShops.documents.mapNotNull { document ->
                        val data = document.data ?: return@mapNotNull null
                        val shopLat = data["latitude"] as? Double ?: return@mapNotNull null
                        val shopLng = data["longitude"] as? Double ?: return@mapNotNull null
                        
                        CoffeeShopEntity(
                            placeId = document.id,
                            name = data["name"] as? String ?: "",
                            rating = (data["rating"] as? Double)?.toFloat() ?: 0f,
                            description = data["description"] as? String ?: "",
                            latitude = shopLat,
                            longitude = shopLng,
                            address = data["address"] as? String,
                            photoUrl = data["photoUrl"] as? String,
                            tags = data["tags"] as? List<String> ?: emptyList()
                        )
                    }
                    coffeeShopDao.insertCoffeeShops(entities)
                    Log.d(TAG, "Cached ${entities.size} coffee shops to Room for future nearby queries")
                }
            }
            
            nearbyShops
        } catch (e: Exception) {
            Log.e(TAG, "Error getting nearby shops", e)
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

    suspend fun getCoffeeShopByName(name: String, forceRefresh: Boolean = false): CoffeeShop? {
        return try {
            // First try to get from local database if not forcing refresh and cache is not stale
            if (!forceRefresh && !isCacheStale()) {
                val cachedShops = withContext(Dispatchers.IO) {
                    coffeeShopDao.searchCoffeeShops("%$name%").map { it.toCoffeeShop() }
                }
                
                val exactMatch = cachedShops.find { it.name.equals(name, ignoreCase = true) }
                if (exactMatch != null) {
                    Log.d(TAG, "Using cached coffee shop with name '$name' (still fresh)")
                    return exactMatch
                }
            }
            
            // Refresh from Firestore if forced, cache miss, or cache is stale
            Log.d(TAG, "Fetching coffee shop by name '$name' from Firestore")
            
            val snapshot = coffeeShopsCollection
                .whereEqualTo("name", name)
                .limit(1)
                .get()
                .await()
                
            if (!snapshot.isEmpty) {
                val document = snapshot.documents[0]
                val data = document.data
                val coffeeShop = CoffeeShop(
                    name = data?.get("name") as? String ?: "",
                    rating = (data?.get("rating") as? Double)?.toFloat() ?: 0f,
                    description = data?.get("description") as? String ?: "",
                    latitude = (data?.get("latitude") as? Double) ?: 0.0,
                    longitude = (data?.get("longitude") as? Double) ?: 0.0,
                    placeId = document.id,
                    photoUrl = data?.get("photoUrl") as? String,
                    address = data?.get("address") as? String,
                    tags = data?.get("tags") as? List<String> ?: emptyList()
                )
                
                // Update the cache with fresh data
                withContext(Dispatchers.IO) {
                    coffeeShopDao.insertCoffeeShop(CoffeeShopEntity.fromCoffeeShop(coffeeShop))
                    Log.d(TAG, "Updated cache with fresh data for coffee shop with name '$name'")
                }
                
                coffeeShop
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting coffee shop by name: ${e.message}")
            
            // Try to return cached data as fallback
            try {
                val cachedShops = withContext(Dispatchers.IO) {
                    coffeeShopDao.searchCoffeeShops("%$name%").map { it.toCoffeeShop() }
                }
                
                val exactMatch = cachedShops.find { it.name.equals(name, ignoreCase = true) }
                if (exactMatch != null) {
                    Log.d(TAG, "Returning cached coffee shop as fallback after error")
                    return exactMatch
                }
            } catch (cacheError: Exception) {
                Log.e(TAG, "Error getting from cache: ${cacheError.message}")
            }
            
            null
        }
    }
    
    // Add method to refresh the cache with latest data from Firestore
    suspend fun refreshCache() {
        try {
            Log.d(TAG, "Refreshing coffee shops cache from Firestore")
            val snapshot = coffeeShopsCollection.get().await()
            val coffeeShops = snapshot.documents.mapNotNull { document ->
                try {
                    CoffeeShop(
                        name = document.getString("name") ?: "",
                        rating = document.getDouble("rating")?.toFloat(),
                        description = document.getString("description") ?: "",
                        latitude = document.getDouble("latitude") ?: 0.0,
                        longitude = document.getDouble("longitude") ?: 0.0,
                        address = document.getString("address"),
                        photoUrl = document.getString("photoUrl"),
                        placeId = document.id,
                        tags = document.get("tags") as? List<String> ?: emptyList()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error mapping document: ${document.id}", e)
                    null
                }
            }
            
            withContext(Dispatchers.IO) {
                // Clear the existing cache
                coffeeShopDao.deleteAllCoffeeShops()
                
                // Insert the fresh data
                val entities = coffeeShops.map { CoffeeShopEntity.fromCoffeeShop(it) }
                coffeeShopDao.insertCoffeeShops(entities)
                Log.d(TAG, "Refreshed cache with ${entities.size} coffee shops")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing cache", e)
        }
    }
    
    // Add method to clear the cache
    suspend fun clearCache() {
        try {
            withContext(Dispatchers.IO) {
                coffeeShopDao.deleteAllCoffeeShops()
                Log.d(TAG, "Cleared coffee shops cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    /**
     * Get featured coffees from Firestore
     * Now returns CoffeeShop objects instead of Coffee objects
     */
    suspend fun getFeaturedCoffees(): List<CoffeeShop> {
        return try {
            Log.d(TAG, "Fetching featured coffees from Firestore")
            val result = db.collection("Coffees")
                .whereEqualTo("featured", true)
                .get()
                .await()
            
            result.documents.mapNotNull { doc ->
                try {
                    // Map Coffee data to CoffeeShop 
                    CoffeeShop(
                        name = doc.getString("name") ?: "",
                        description = doc.getString("description") ?: "",
                        rating = doc.getDouble("rating")?.toFloat(),
                        latitude = 0.0,  // Default to 0,0 for products without location
                        longitude = 0.0,
                        photoUrl = doc.getString("imageUrl"),
                        placeId = doc.id,
                        tags = doc.get("tags") as? List<String> ?: emptyList()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error mapping coffee document: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting featured coffees: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get coffees categorized by category name
     * Now returns CoffeeShop objects instead of Coffee objects
     */
    suspend fun getCategorizedCoffees(): Map<String, List<CoffeeShop>> {
        return try {
            Log.d(TAG, "Fetching categorized coffees from Firestore")
            // First, get all categories
            val categoriesResult = db.collection("CoffeeCategories")
                .get()
                .await()
            
            val categories = categoriesResult.documents.mapNotNull { doc ->
                doc.getString("name")
            }.distinct()
            
            // Then load coffees for each category
            val categorizedMap = mutableMapOf<String, List<CoffeeShop>>()
            
            for (category in categories) {
                val coffeesResult = db.collection("Coffees")
                    .whereEqualTo("categoryName", category)
                    .get()
                    .await()
                
                val coffeeShops = coffeesResult.documents.mapNotNull { doc ->
                    try {
                        // Map Coffee data to CoffeeShop
                        CoffeeShop(
                            name = doc.getString("name") ?: "",
                            description = doc.getString("description") ?: "",
                            rating = doc.getDouble("rating")?.toFloat(),
                            latitude = 0.0,  // Default to 0,0 for products without location
                            longitude = 0.0,
                            photoUrl = doc.getString("imageUrl"),
                            placeId = doc.id,
                            tags = doc.get("tags") as? List<String> ?: emptyList()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error mapping coffee document: ${e.message}")
                        null
                    }
                }
                
                if (coffeeShops.isNotEmpty()) {
                    categorizedMap[category] = coffeeShops
                }
            }
            
            categorizedMap
        } catch (e: Exception) {
            Log.e(TAG, "Error getting categorized coffees: ${e.message}")
            emptyMap()
        }
    }
    
    /**
     * Add a coffee shop to user's favorites
     */
    suspend fun addToFavorites(coffeeShop: CoffeeShop) {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("User must be logged in")
            
            val favoriteData = hashMapOf(
                "userId" to currentUser.uid,
                "coffeeShopId" to (coffeeShop.placeId ?: ""),
                "timestamp" to System.currentTimeMillis()
            )
            
            db.collection("FavoriteCoffeeShops")
                .add(favoriteData)
                .await()
                
            Log.d(TAG, "Added coffee shop ${coffeeShop.placeId} to favorites for user ${currentUser.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding coffee shop to favorites: ${e.message}")
            throw e
        }
    }

    // Add method to migrate coffee shop photos from Google Places to Firebase Storage
    suspend fun migratePhotosToFirebaseStorage() {
        try {
            Log.d(TAG, "Starting migration of coffee shop photos to Firebase Storage")
            
            // Get all coffee shops from Firestore
            val snapshot = coffeeShopsCollection.get().await()
            var migratedCount = 0
            
            for (document in snapshot.documents) {
                val placeId = document.id
                val photoUrl = document.getString("photoUrl")
                
                // Check if the photo URL is a Google Places URL that contains the API key
                if (photoUrl != null && photoUrl.contains("maps.googleapis.com/maps/api/place/photo")) {
                    Log.d(TAG, "Found Google Places URL for shop: ${document.getString("name")}")
                    
                    try {
                        // Download the image from the URL using Picasso
                        val future = com.squareup.picasso.Picasso.get().load(photoUrl).get()
                        val bitmap = future
                        
                        // Convert bitmap to byte array
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                        val imageData = baos.toByteArray()
                        
                        // Upload to Firebase Storage
                        val storageRef = FirebaseStorage.getInstance().reference
                            .child("CoffeeShops")
                            .child("$placeId.jpg")
                        
                        // Upload photo to Firebase Storage
                        storageRef.putBytes(imageData).await()
                        
                        // Get the download URL
                        val uri = storageRef.downloadUrl.await()
                        val newPhotoUrl = uri.toString()
                        
                        Log.d(TAG, "Uploaded to Firebase Storage: $newPhotoUrl")
                        
                        // Update Firestore with new URL
                        coffeeShopsCollection.document(placeId)
                            .update("photoUrl", newPhotoUrl)
                            .await()
                        
                        // Update local cache
                        withContext(Dispatchers.IO) {
                            val entity = coffeeShopDao.getCoffeeShopById(placeId)
                            if (entity != null) {
                                val updatedEntity = entity.copy(photoUrl = newPhotoUrl)
                                coffeeShopDao.insertCoffeeShop(updatedEntity)
                            }
                        }
                        
                        migratedCount++
                        Log.d(TAG, "Successfully migrated photo for $placeId")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error migrating photo for shop $placeId: ${e.message}", e)
                    }
                }
            }
            
            Log.d(TAG, "Migration completed. Migrated $migratedCount coffee shop photos")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in photo migration process: ${e.message}", e)
        }
    }

    companion object {
        @Volatile
        private var instance: CoffeeShopRepository? = null

        fun getInstance(context: Context): CoffeeShopRepository {
            return instance ?: synchronized(this) {
                instance ?: CoffeeShopRepository(context.applicationContext).also { instance = it }
            }
        }
    }
} 