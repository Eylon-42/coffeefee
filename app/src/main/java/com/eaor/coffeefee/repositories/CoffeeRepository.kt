package com.eaor.coffeefee.repositories

import com.eaor.coffeefee.models.Coffee
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository for handling coffee-related data operations
 */
class CoffeeRepository private constructor() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        @Volatile
        private var instance: CoffeeRepository? = null
        
        fun getInstance(): CoffeeRepository {
            return instance ?: synchronized(this) {
                instance ?: CoffeeRepository().also { instance = it }
            }
        }
    }
    
    /**
     * Get featured coffees from Firestore
     */
    suspend fun getFeaturedCoffees(): List<Coffee> {
        val result = db.collection("Coffees")
            .whereEqualTo("featured", true)
            .get()
            .await()
        
        return result.documents.mapNotNull { doc ->
            try {
                Coffee(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    description = doc.getString("description") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "",
                    categoryId = doc.getString("categoryId") ?: "",
                    categoryName = doc.getString("categoryName") ?: "",
                    price = doc.getDouble("price") ?: 0.0,
                    rating = doc.getDouble("rating") ?: 0.0,
                    shopId = doc.getString("shopId") ?: "",
                    isFeatured = doc.getBoolean("featured") ?: false
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Get coffees categorized by category name
     */
    suspend fun getCategorizedCoffees(): Map<String, List<Coffee>> {
        // First, get all categories
        val categoriesResult = db.collection("CoffeeCategories")
            .get()
            .await()
        
        val categories = categoriesResult.documents.mapNotNull { doc ->
            doc.getString("name")
        }.distinct()
        
        // Then load coffees for each category
        val categorizedMap = mutableMapOf<String, List<Coffee>>()
        
        for (category in categories) {
            val coffeesResult = db.collection("Coffees")
                .whereEqualTo("categoryName", category)
                .get()
                .await()
            
            val coffees = coffeesResult.documents.mapNotNull { doc ->
                try {
                    Coffee(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        description = doc.getString("description") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        categoryId = doc.getString("categoryId") ?: "",
                        categoryName = doc.getString("categoryName") ?: "",
                        price = doc.getDouble("price") ?: 0.0,
                        rating = doc.getDouble("rating") ?: 0.0,
                        shopId = doc.getString("shopId") ?: "",
                        isFeatured = doc.getBoolean("featured") ?: false
                    )
                } catch (e: Exception) {
                    null
                }
            }
            
            if (coffees.isNotEmpty()) {
                categorizedMap[category] = coffees
            }
        }
        
        return categorizedMap
    }
    
    /**
     * Add a coffee to user's favorites
     */
    suspend fun addToFavorites(coffee: Coffee) {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User must be logged in")
        
        val favoriteData = hashMapOf(
            "userId" to currentUser.uid,
            "coffeeId" to coffee.id,
            "timestamp" to System.currentTimeMillis()
        )
        
        db.collection("FavoriteCoffees")
            .add(favoriteData)
            .await()
    }
    
    /**
     * Get a coffee by its ID
     */
    suspend fun getCoffeeById(coffeeId: String): Coffee? {
        val doc = db.collection("Coffees").document(coffeeId).get().await()
        
        return if (doc.exists()) {
            try {
                Coffee(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    description = doc.getString("description") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "",
                    categoryId = doc.getString("categoryId") ?: "",
                    categoryName = doc.getString("categoryName") ?: "",
                    price = doc.getDouble("price") ?: 0.0,
                    rating = doc.getDouble("rating") ?: 0.0,
                    shopId = doc.getString("shopId") ?: "",
                    isFeatured = doc.getBoolean("featured") ?: false
                )
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
} 