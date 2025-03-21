package com.eaor.coffeefee.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.models.Coffee
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.repositories.CoffeeRepository
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for handling coffee data and operations
 */
class CoffeeViewModel : BaseViewModel() {
    
    // Repositories
    private val coffeeRepository = CoffeeRepository.getInstance()
    private val coffeeShopRepository = CoffeeShopRepository.getInstance()
    
    // LiveData for the selected coffee shop
    private val _selectedCoffeeShop = MutableLiveData<CoffeeShop?>()
    val selectedCoffeeShop: LiveData<CoffeeShop?> = _selectedCoffeeShop
    
    // LiveData for nearby coffee shops
    private val _coffeeShops = MutableLiveData<List<CoffeeShop>>()
    val coffeeShops: LiveData<List<CoffeeShop>> = _coffeeShops
    
    // LiveData for featured coffees
    private val _featuredCoffees = MutableLiveData<List<Coffee>>()
    val featuredCoffees: LiveData<List<Coffee>> = _featuredCoffees
    
    // LiveData for coffee categories
    private val _coffeeCategories = MutableLiveData<List<String>>()
    val coffeeCategories: LiveData<List<String>> = _coffeeCategories
    
    // LiveData for coffees by category
    private val _categorizedCoffees = MutableLiveData<Map<String, List<Coffee>>>()
    val categorizedCoffees: LiveData<Map<String, List<Coffee>>> = _categorizedCoffees
    
    /**
     * Get a coffee shop by its place ID
     */
    fun getCoffeeShopByPlaceId(placeId: String) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val coffeeShop = coffeeShopRepository.getCoffeeShop(placeId)
                _selectedCoffeeShop.value = coffeeShop
                
                // Also load nearby shops
                loadNearbyCoffeeShops(coffeeShop?.latitude, coffeeShop?.longitude)
                
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
    fun getCoffeeShopByName(name: String) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val coffeeShop = coffeeShopRepository.getCoffeeShopByName(name)
                _selectedCoffeeShop.value = coffeeShop
                
                // Also load nearby shops
                loadNearbyCoffeeShops(coffeeShop?.latitude, coffeeShop?.longitude)
                
                clearError()
            } catch (e: Exception) {
                setError("Error loading coffee shop: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Load nearby coffee shops
     */
    fun loadNearbyCoffeeShops(centerLat: Double? = null, centerLng: Double? = null) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val shops = if (centerLat != null && centerLng != null) {
                    // If we have coordinates, use getNearbyShops
                    coffeeShopRepository.getNearbyShops(centerLat, centerLng)
                } else {
                    // Otherwise just load all shops
                    coffeeShopRepository.getAllCoffeeShops().first()
                }
                
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
                val coffees = coffeeRepository.getFeaturedCoffees()
                _featuredCoffees.value = coffees
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
                val categorized = coffeeRepository.getCategorizedCoffees()
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
     * Add a coffee to favorites
     */
    fun addCoffeeToFavorites(coffee: Coffee) {
        viewModelScope.launch {
            try {
                coffeeRepository.addToFavorites(coffee)
                clearError()
            } catch (e: Exception) {
                setError("Error adding to favorites: ${e.message}")
            }
        }
    }
} 