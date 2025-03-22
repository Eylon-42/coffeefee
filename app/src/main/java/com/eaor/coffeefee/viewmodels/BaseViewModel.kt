package com.eaor.coffeefee.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Base ViewModel that provides common functionality for all ViewModels
 */
abstract class BaseViewModel : ViewModel() {
    // Common Firebase instances
    open val auth = FirebaseAuth.getInstance()
    open val db = FirebaseFirestore.getInstance()
    
    // Common loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Common error message
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    /**
     * Set the loading state
     */
    protected fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
    
    /**
     * Set an error message
     */
    protected fun setError(message: String?) {
        _errorMessage.value = message
    }
    
    /**
     * Clear any error message
     */
    protected fun clearError() {
        _errorMessage.value = null
    }
} 