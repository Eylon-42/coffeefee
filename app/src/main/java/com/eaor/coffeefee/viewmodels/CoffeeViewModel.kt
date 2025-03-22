package com.eaor.coffeefee.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for handling coffee data and operations
 */
class CoffeeViewModel : BaseViewModel() {
    
    // Repository
    private lateinit var coffeeShopRepository: CoffeeShopRepository
    
    // LiveData for the selected coffee shop
    private val _selectedCoffeeShop = MutableLiveData<CoffeeShop?>()
    val selectedCoffeeShop: LiveData<CoffeeShop?> = _selectedCoffeeShop
    
    // LiveData for nearby coffee shops
    private val _coffeeShops = MutableLiveData<List<CoffeeShop>>()
    val coffeeShops: LiveData<List<CoffeeShop>> = _coffeeShops
    
    // LiveData for featured coffees (now using CoffeeShop)
    private val _featuredCoffees = MutableLiveData<List<CoffeeShop>>()
    val featuredCoffees: LiveData<List<CoffeeShop>> = _featuredCoffees
    
    // LiveData for coffee categories
    private val _coffeeCategories = MutableLiveData<List<String>>()
    val coffeeCategories: LiveData<List<String>> = _coffeeCategories
    
    // LiveData for coffees by category (now using CoffeeShop)
    private val _categorizedCoffees = MutableLiveData<Map<String, List<CoffeeShop>>>()
    val categorizedCoffees: LiveData<Map<String, List<CoffeeShop>>> = _categorizedCoffees
    
    // Initialize repository with context
    fun initialize(context: Context) {
        coffeeShopRepository = CoffeeShopRepository.getInstance(context)
        
        // Run the migration process for coffee shop photos
        migratePhotosToFirebaseStorage()
    }
    
    /**
     * Migrate coffee shop photos from Google Places URLs to Firebase Storage
     */
    private fun migratePhotosToFirebaseStorage() {
        viewModelScope.launch {
            try {
                coffeeShopRepository.migratePhotosToFirebaseStorage()
            } catch (e: Exception) {
                // Log but don't show error to user - this is a background task
                Log.e("CoffeeViewModel", "Error during photo migration: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get a coffee shop by its place ID
     */
    fun getCoffeeShopByPlaceId(placeId: String, forceRefresh: Boolean = false) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                // Let the repository decide if refresh is needed based on cache staleness
                val coffeeShop = coffeeShopRepository.getCoffeeShop(placeId, forceRefresh = forceRefresh)
                _selectedCoffeeShop.value = coffeeShop
                
                // Load all coffee shops instead of just nearby
                loadAllCoffeeShops()
                
                clearError()
            } catch (e: Exception) {
                setError("Error loading coffee shop: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Get a coffee shop by its name
     */
    fun getCoffeeShopByName(name: String, forceRefresh: Boolean = false) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                // Let the repository decide if refresh is needed based on cache staleness
                val coffeeShop = coffeeShopRepository.getCoffeeShopByName(name, forceRefresh = forceRefresh)
                _selectedCoffeeShop.value = coffeeShop
                
                // Load all coffee shops instead of just nearby
                loadAllCoffeeShops()
                
                clearError()
            } catch (e: Exception) {
                setError("Error loading coffee shop by name: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Load all coffee shops
     */
    fun loadAllCoffeeShops() {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                // Use smart caching strategy
                val shops = coffeeShopRepository.getAllCoffeeShops(forceRefresh = false)
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
    
    /**
     * Load featured coffees
     */
    fun loadFeaturedCoffees() {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val coffeeShops = coffeeShopRepository.getFeaturedCoffees()
                _featuredCoffees.value = coffeeShops
                clearError()
            } catch (e: Exception) {
                setError("Error loading featured coffees: ${e.message}")
                _featuredCoffees.value = emptyList()
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Load coffee categories
     */
    fun loadCoffeeCategories() {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val categorized = coffeeShopRepository.getCategorizedCoffees()
                _categorizedCoffees.value = categorized
                clearError()
            } catch (e: Exception) {
                setError("Error loading coffee categories: ${e.message}")
                _categorizedCoffees.value = emptyMap()
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Refresh the coffee shops data from the server
     */
    fun refreshCoffeeShops() {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                // This is an explicit refresh request, so force it
                val shops = coffeeShopRepository.getAllCoffeeShops(forceRefresh = true)
                _coffeeShops.value = shops
                clearError()
            } catch (e: Exception) {
                setError("Error refreshing coffee shops: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Search for coffee shops by name
     */
    fun searchCoffeeShops(query: String) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                // Use smart caching strategy
                val shops = coffeeShopRepository.searchCoffeeShops(query, forceRefresh = false)
                _coffeeShops.value = shops
                clearError()
            } catch (e: Exception) {
                setError("Error searching coffee shops: ${e.message}")
                _coffeeShops.value = emptyList()
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Add a coffee shop to favorites
     */
    fun addToFavorites(coffeeShop: CoffeeShop) {
        viewModelScope.launch {
            try {
                coffeeShopRepository.addToFavorites(coffeeShop)
                clearError()
            } catch (e: Exception) {
                setError("Error adding to favorites: ${e.message}")
            }
        }
    }
} 