package com.eaor.coffeefee.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import com.eaor.coffeefee.utils.VertexAIService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SuggestionViewModel : BaseViewModel() {
    override val db = FirebaseFirestore.getInstance()
    override val auth = FirebaseAuth.getInstance()
    private val vertexAIService = VertexAIService.getInstance()
    private lateinit var repository: CoffeeShopRepository
    
    // Function to initialize repository with context
    fun initializeRepository(context: Context) {
        if (!::repository.isInitialized) {
            repository = CoffeeShopRepository.getInstance(context)
        }
    }
    
    // LiveData for suggested coffee shops with their matching reasons
    private val _suggestedCoffeeShops = MutableLiveData<List<Pair<CoffeeShop, List<String>>>>()
    val suggestedCoffeeShops: LiveData<List<Pair<CoffeeShop, List<String>>>> = _suggestedCoffeeShops
    
    // LiveData for user tags
    private val _userTags = MutableLiveData<List<String>>()
    val userTags: LiveData<List<String>> = _userTags
    
    /**
     * Load the user's preference tags from their profile
     */
    fun loadUserTags(forceRefresh: Boolean = false) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            setError("User not logged in")
            return
        }
        
        setLoading(true)
        
        viewModelScope.launch {
            try {
                // Get user tags directly from Firestore
                val userSnapshot = db.collection("Users")
                    .document(userId)
                    .get()
                    .await()
                
                Log.d(TAG, "User tags snapshot: $userSnapshot")
                
                val tags = userSnapshot.get("tags") as? List<String> ?: emptyList()
                _userTags.value = tags
                
                if (tags.isEmpty()) {
                    Log.w(TAG, "User has no preference tags")
                    setError("Please update your preferences to get personalized recommendations")
                    setLoading(false)
                } else {
                    Log.d(TAG, "Loaded ${tags.size} user tags")
                    // Generate suggestions based on the new tags
                    generateSuggestions(forceRefresh)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user tags: ${e.message}", e)
                setError("Error loading user preferences: ${e.message}")
                _userTags.value = emptyList()
                setLoading(false)
            }
        }
    }
    
    /**
     * Generate coffee shop suggestions based on the user's tags
     */
    fun generateSuggestions(forceRefresh: Boolean = false) {
        val tags = _userTags.value
        if (tags.isNullOrEmpty()) {
            setError("No user preferences available")
            setLoading(false)
            return
        }
        
        if (!::repository.isInitialized) {
            setError("Repository not initialized")
            setLoading(false)
            return
        }
        
        setLoading(true)
        
        viewModelScope.launch {
            try {
                // First get all coffee shops
                val coffeeShops = repository.getAllCoffeeShops(forceRefresh = forceRefresh)
                
                if (coffeeShops.isEmpty()) {
                    Log.e(TAG, "No coffee shops available for generating suggestions")
                    setError("No coffee shops available")
                    _suggestedCoffeeShops.value = emptyList()
                    setLoading(false)
                    return@launch
                }
                
                // Use VertexAI to generate suggestions
                val result = vertexAIService.generateCoffeeShopSuggestions(tags, coffeeShops)
                
                result.fold(
                    onSuccess = { suggestions ->
                        Log.d(TAG, "Generated ${suggestions.size} suggestions")
                        _suggestedCoffeeShops.value = suggestions
                        if (suggestions.isEmpty()) {
                            setError("No suggestions found for your preferences")
                        } else {
                            clearError()
                        }
                        setLoading(false)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error generating suggestions: ${error.message}", error)
                        setError("Error generating suggestions: ${error.message}")
                        
                        // Fall back to simple tag matching
                        _suggestedCoffeeShops.value = generateFallbackSuggestions(tags, coffeeShops)
                        setLoading(false)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception in suggestion generation: ${e.message}", e)
                setError("Error generating suggestions: ${e.message}")
                _suggestedCoffeeShops.value = emptyList()
                setLoading(false)
            }
        }
    }
    
    /**
     * Generate fallback suggestions when AI fails
     */
    private fun generateFallbackSuggestions(
        userTags: List<String>,
        coffeeShops: List<CoffeeShop>
    ): List<Pair<CoffeeShop, List<String>>> {
        return coffeeShops
            .filter { shop -> 
                shop.tags.any { tag -> userTags.contains(tag) }
            }
            .map { shop ->
                val matchingTags = shop.tags.filter { userTags.contains(it) }
                Pair(shop, matchingTags)
            }
            .sortedByDescending { it.second.size }
            .take(5)
    }
    
    companion object {
        private const val TAG = "SuggestionViewModel"
    }
} 