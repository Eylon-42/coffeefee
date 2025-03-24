package com.eaor.coffeefee.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.models.User
import com.eaor.coffeefee.models.UserPreferences
import com.eaor.coffeefee.repositories.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * ViewModel for working with the complete User model
 * This handles the full Firestore User schema including preferences and tags
 */
class UserProfileViewModel : ViewModel() {
    // Firebase Auth instance
    private val auth = FirebaseAuth.getInstance()
    
    // Repository - to be injected
    private lateinit var userRepository: UserRepository
    
    // Current user data
    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> = _userData
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error state
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error
    
    // Success state for updates
    private val _updateSuccess = MutableLiveData<Boolean>(false)
    val updateSuccess: LiveData<Boolean> = _updateSuccess
    
    // Initialize with repository
    fun initialize(repository: UserRepository) {
        userRepository = repository
    }
    
    /**
     * Load the complete user data from Firestore
     */
    fun loadUserData(userId: String? = null) {
        val targetUserId = userId ?: auth.currentUser?.uid
        
        if (targetUserId == null) {
            _error.value = "No user ID provided"
            return
        }
        
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                val user = userRepository.getCompleteUserData(targetUserId)
                _userData.value = user
                _isLoading.value = false
                
                if (user == null) {
                    _error.value = "User not found"
                }
            } catch (e: Exception) {
                Log.e("UserProfileViewModel", "Error loading user data", e)
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update user preferences
     */
    fun updateUserPreferences(
        dietaryNeeds: String,
        favoriteCoffeeDrink: String,
        locationPreference: String,
        preferredAtmosphere: String
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _error.value = "User not logged in"
            return
        }
        
        val currentUser = _userData.value
        if (currentUser == null) {
            _error.value = "No user data loaded"
            return
        }
        
        val updatedPreferences = UserPreferences(
            dietaryNeeds = dietaryNeeds,
            favoriteCoffeeDrink = favoriteCoffeeDrink,
            locationPreference = locationPreference,
            preferredAtmosphere = preferredAtmosphere
        )
        
        val updatedUser = currentUser.copy(preferences = updatedPreferences)
        updateUser(updatedUser)
    }
    
    /**
     * Update user tags
     */
    fun updateUserTags(tags: List<String>) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _error.value = "User not logged in"
            return
        }
        
        val currentUser = _userData.value
        if (currentUser == null) {
            _error.value = "No user data loaded"
            return
        }
        
        val updatedUser = currentUser.copy(tags = tags)
        updateUser(updatedUser)
    }
    
    /**
     * Update the entire user
     */
    private fun updateUser(user: User) {
        _isLoading.value = true
        _updateSuccess.value = false
        
        viewModelScope.launch {
            try {
                val success = userRepository.updateCompleteUser(user)
                
                if (success) {
                    _userData.value = user
                    _updateSuccess.value = true
                } else {
                    _error.value = "Failed to update user data"
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e("UserProfileViewModel", "Error updating user", e)
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Reset update success flag
     */
    fun resetUpdateSuccess() {
        _updateSuccess.value = false
    }
} 