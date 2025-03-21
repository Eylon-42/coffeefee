package com.eaor.coffeefee.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : BaseViewModel() {
    private val _currentUser = MutableLiveData<FirebaseUser?>()
    val currentUser: LiveData<FirebaseUser?> = _currentUser
    
    init {
        _currentUser.value = auth.currentUser
    }
    
    fun signIn(email: String, password: String) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                _currentUser.value = result.user
                clearError()
                setLoading(false)
            } catch (e: Exception) {
                setError(e.message)
                setLoading(false)
            }
        }
    }
    
    fun register(email: String, password: String, displayName: String) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                
                // Update user profile
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                
                result.user?.updateProfile(profileUpdates)?.await()
                
                // Create user document in Firestore
                result.user?.uid?.let { uid ->
                    val userData = mapOf(
                        "userId" to uid,
                        "displayName" to displayName,
                        "email" to email
                    )
                    
                    db.collection("users").document(uid)
                        .set(userData)
                        .await()
                }
                
                _currentUser.value = result.user
                clearError()
                setLoading(false)
            } catch (e: Exception) {
                setError(e.message)
                setLoading(false)
            }
        }
    }
    
    fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }
    
    fun getCurrentUser() {
        _currentUser.value = auth.currentUser
    }
    
    fun resetPassword(email: String) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                setLoading(false)
                clearError()
            } catch (e: Exception) {
                setError(e.message)
                setLoading(false)
            }
        }
    }
} 