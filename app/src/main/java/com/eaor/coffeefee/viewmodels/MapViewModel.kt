package com.eaor.coffeefee.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.models.FeedItem
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MapViewModel : BaseViewModel() {
    private lateinit var repository: CoffeeShopRepository
    
    // Coffee shops LiveData
    private val _coffeeShops = MutableLiveData<List<CoffeeShop>>()
    val coffeeShops: LiveData<List<CoffeeShop>> = _coffeeShops
    
    // Map posts LiveData
    private val _mapPosts = MutableLiveData<List<FeedItem>>()
    val mapPosts: LiveData<List<FeedItem>> = _mapPosts
    
    // Selected coffee shop LiveData
    private val _selectedShop = MutableLiveData<CoffeeShop?>()
    val selectedShop: LiveData<CoffeeShop?> = _selectedShop
    
    // User current location
    private val _userLocation = MutableLiveData<LatLng>()
    val userLocation: LiveData<LatLng> = _userLocation
    
    // Initialize the repository with context
    fun initialize(context: Context) {
        repository = CoffeeShopRepository.getInstance(context)
    }
    
    // Load coffee shops from Firestore
    fun loadCoffeeShops() {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val shops = repository.getAllCoffeeShops()
                _coffeeShops.value = shops
                clearError()
            } catch (e: Exception) {
                setError("Error loading coffee shops: ${e.message}")
                _coffeeShops.value = emptyList()
            } finally {
                setLoading(false)
            }
        }
    }
    
    // Load posts that have coffee shop information
    fun loadMapPosts() {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val result = db.collection("Posts")
                    .whereNotEqualTo("coffeeShop", null)
                    .get()
                    .await()
                
                val posts = result.documents.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        
                        FeedItem(
                            id = doc.id,
                            userId = data["userId"] as? String ?: return@mapNotNull null,
                            userName = data["userName"] as? String ?: "",
                            experienceDescription = data["content"] as? String ?: "",
                            photoUrl = data["imageUrl"] as? String,
                            timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
                            commentCount = (data["commentCount"] as? Long)?.toInt() ?: 0,
                            likeCount = (data["likesCount"] as? Long)?.toInt() ?: 0
                        )
                    } catch (e: Exception) {
                        setError("Error parsing map post: ${e.message}")
                        null
                    }
                }
                
                _mapPosts.value = posts
                clearError()
            } catch (e: Exception) {
                setError("Error loading map posts: ${e.message}")
                _mapPosts.value = emptyList()
            } finally {
                setLoading(false)
            }
        }
    }
    
    // Set the selected coffee shop
    fun selectCoffeeShop(shop: CoffeeShop?) {
        _selectedShop.value = shop
    }
    
    // Set the user's current location
    fun setUserLocation(location: LatLng) {
        _userLocation.value = location
    }
    
    // Get posts for a specific coffee shop
    fun getPostsForCoffeeShop(coffeeShopPlaceId: String): List<FeedItem> {
        return _mapPosts.value?.filter { post ->
            // As a temporary measure, just return all posts
            // Ideally, we would filter by a coffee shop ID or other identifier
            true
        } ?: emptyList()
    }
    
    // Add a new coffee shop
    fun addCoffeeShop(shop: CoffeeShop) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val shopData = hashMapOf(
                    "name" to shop.name,
                    "latitude" to shop.latitude,
                    "longitude" to shop.longitude,
                    "address" to shop.address,
                    "photoUrl" to shop.photoUrl,
                    "rating" to shop.rating,
                    "placeId" to shop.placeId
                )
                
                val result = db.collection("CoffeeShops").add(shopData).await()
                
                // Add the new shop to the list
                val newShop = shop  // Create a reference to the original shop
                val currentShops = _coffeeShops.value?.toMutableList() ?: mutableListOf()
                currentShops.add(newShop)
                _coffeeShops.value = currentShops
                
                clearError()
            } catch (e: Exception) {
                setError("Error adding coffee shop: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }
    
    fun loadPostsWithLocation() {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val result = db.collection("Posts")
                    .whereNotEqualTo("location", null)
                    .get()
                    .await()

                val posts = result.documents.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        
                        FeedItem(
                            id = doc.id,
                            userId = data["userId"] as? String ?: return@mapNotNull null,
                            userName = data["userName"] as? String ?: "",
                            experienceDescription = data["content"] as? String ?: "",
                            photoUrl = data["imageUrl"] as? String,
                            timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
                            commentCount = (data["commentCount"] as? Long)?.toInt() ?: 0,
                            likeCount = (data["likesCount"] as? Long)?.toInt() ?: 0
                        )
                    } catch (e: Exception) {
                        setError("Error parsing post with location: ${e.message}")
                        null
                    }
                }
                
                _mapPosts.value = posts
                setLoading(false)
            } catch (e: Exception) {
                setError("Error loading posts with location: ${e.message}")
                setLoading(false)
            }
        }
    }
    
    // Get coffee shop by place ID
    fun getCoffeeShopByPlaceId(placeId: String) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val result = db.collection("CoffeeShops")
                    .whereEqualTo("placeId", placeId)
                    .get()
                    .await()
                
                if (!result.isEmpty) {
                    val doc = result.documents.first()
                    val shop = CoffeeShop(
                        name = doc.getString("name") ?: "",
                        description = doc.getString("caption") ?: "",
                        rating = doc.getDouble("rating")?.toFloat(),
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        address = doc.getString("address"),
                        photoUrl = doc.getString("photoUrl"),
                        placeId = doc.getString("placeId")
                    )
                    _selectedShop.value = shop
                }
            } catch (e: Exception) {
                setError("Error finding coffee shop: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }
    
    fun getCoffeeShopByLocation(latLng: LatLng, name: String? = null) {
        viewModelScope.launch {
            try {
                val shops = _coffeeShops.value ?: repository.getAllCoffeeShops()
                
                val nearbyShop = shops.minByOrNull { shop ->
                    val shopLatLng = LatLng(shop.latitude, shop.longitude)
                    calculateDistance(latLng, shopLatLng)
                }
                
                if (nearbyShop != null) {
                    _selectedShop.value = nearbyShop
                } else {
                    setError("No coffee shop found near this location")
                }
            } catch (e: Exception) {
                setError("Error finding coffee shop: ${e.message}")
            }
        }
    }
    
    // Simple distance calculation for finding nearby shops
    private fun calculateDistance(latLng1: LatLng, latLng2: LatLng): Float {
        val lat1 = latLng1.latitude
        val lon1 = latLng1.longitude
        val lat2 = latLng2.latitude
        val lon2 = latLng2.longitude
        
        val radius = 6371 // Earth's radius in kilometers
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return (radius * c).toFloat()
    }
} 