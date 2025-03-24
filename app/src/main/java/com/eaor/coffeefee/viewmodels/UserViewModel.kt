package com.eaor.coffeefee.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.data.UserEntity
import com.eaor.coffeefee.repositories.FeedRepository
import com.eaor.coffeefee.repositories.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import com.eaor.coffeefee.GlobalState

class UserViewModel : ViewModel() {
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Change this to public, but give it a different name for external use
    private val _userData = MutableLiveData<UserEntity?>()
    val userData: LiveData<UserEntity?> = _userData
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _updateSuccess = MutableLiveData<Boolean>()
    val updateSuccess: LiveData<Boolean> = _updateSuccess
    
    private val _uploadProgress = MutableLiveData<Int>()
    val uploadProgress: LiveData<Int> = _uploadProgress
    
    // Add repository as a parameter to initialize it
    private lateinit var userRepository: UserRepository
    private lateinit var feedRepository: FeedRepository
    
    fun initialize(repository: UserRepository, feedRepo: FeedRepository) {
        userRepository = repository
        feedRepository = feedRepo
    }
    
    // Alternative setter methods that match ProfileViewModel pattern
    fun setRepository(repository: FeedRepository) {
        feedRepository = repository
    }
    
    fun setUserRepository(repository: UserRepository) {
        userRepository = repository
    }
    
    fun getUserData(userId: String = auth.currentUser?.uid ?: "") {
        if (userId.isEmpty()) {
            _errorMessage.value = "No user ID provided"
            return
        }
        
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                val user = userRepository.getUserData(userId)
                _userData.value = user
                _isLoading.value = false
                
                if (user == null) {
                    _errorMessage.value = "User not found"
                }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error getting user data: ${e.message}")
                _errorMessage.value = "Error loading user data: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun updateUserData(name: String, email: String, profilePhotoUrl: String? = null) {
        val userId = auth.currentUser?.uid
        if (userId.isNullOrEmpty()) {
            _errorMessage.value = "No user authenticated"
            return
        }
        
        Log.d("UserViewModel", "Starting user update: userId=$userId, name=$name, profileUrl=${profilePhotoUrl ?: "unchanged"}")
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                // Get current user data to preserve fields we're not updating
                val currentUser = _userData.value ?: userRepository.getUserData(userId)
                Log.d("UserViewModel", "Current user: ${currentUser?.name}, ${currentUser?.email}, ${currentUser?.profilePhotoUrl}")
                
                // Use current email instead of the provided one
                val currentEmail = currentUser?.email ?: auth.currentUser?.email ?: email
                
                // Create the updated user object
                val updatedUser = UserEntity(
                    uid = userId,
                    name = name,
                    email = currentEmail, // Keep the current email unchanged
                    profilePhotoUrl = profilePhotoUrl ?: currentUser?.profilePhotoUrl
                )
                
                // Update user data in Firestore and Room
                Log.d("UserViewModel", "About to update user with: ${updatedUser.name}, ${updatedUser.email}, ${updatedUser.profilePhotoUrl}")
                val success = userRepository.updateUser(updatedUser)
                Log.d("UserViewModel", "Update result: $success")
                
                if (success) {
                    _userData.value = updatedUser
                    _updateSuccess.value = true
                    
                    // Update all posts by this user in the local database
                    try {
                        feedRepository.updateUserDataInPosts(
                            userId = userId,
                            userName = name,
                            profilePhotoUrl = updatedUser.profilePhotoUrl
                        )
                        Log.d("UserViewModel", "Updated user data in posts")
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) {
                            Log.w("UserViewModel", "Post update job was cancelled, profile update still successful")
                        } else {
                            Log.e("UserViewModel", "Error updating posts with new user data: ${e.message}")
                        }
                        // Don't rethrow, this is a non-critical operation
                    }
                } else {
                    _errorMessage.value = "Failed to update user data in the database"
                    _updateSuccess.value = false
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error updating user data: ${e.message}")
                _errorMessage.value = "Error updating user data: ${e.message}"
                _isLoading.value = false
                _updateSuccess.value = false
            }
        }
    }
    
    fun uploadProfileImage(imageUri: Uri, userId: String = auth.currentUser?.uid ?: "") {
        if (userId.isEmpty()) {
            _errorMessage.value = "No user ID provided"
            return
        }
        
        _isLoading.value = true
        _uploadProgress.value = 0
        
        // Cache the current name and email to use after upload completes
        val currentName = _userData.value?.name ?: ""
        val currentEmail = _userData.value?.email ?: ""
        
        Log.d("UserViewModel", "Starting image upload for user: $userId, will use name=$currentName, email=$currentEmail")
        
        try {
            // Create a unique filename with timestamp
            val timestamp = System.currentTimeMillis()
            val filename = "profile_${timestamp}_${UUID.randomUUID()}.jpg"
            
            // Create a reference to 'Users/userId/profile_timestamp_uuid.jpg'
            val storageRef = storage.reference.child("Users").child(userId).child(filename)
            
            // Upload the image
            val uploadTask = storageRef.putFile(imageUri)
            
            // Monitor upload progress
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                _uploadProgress.value = progress
            }
            
            // Handle completion
            uploadTask.continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: Exception("Unknown error during upload")
                }
                storageRef.downloadUrl
            }.addOnCompleteListener { task ->
                _isLoading.value = false
                
                if (task.isSuccessful) {
                    val downloadUrl = task.result.toString()
                    Log.d("UserViewModel", "Image upload successful. URL: $downloadUrl")
                    
                    // Update user data with new profile picture URL and preserved name/email
                    viewModelScope.launch {
                        // Get the values from our cached EditText fields
                        val nameToUse = if (currentName.isNotEmpty()) currentName else _userData.value?.name ?: ""
                        val emailToUse = if (currentEmail.isNotEmpty()) currentEmail else _userData.value?.email ?: ""
                        
                        Log.d("UserViewModel", "After image upload, updating user with name=$nameToUse, email=$emailToUse, url=$downloadUrl")
                        updateUserData(nameToUse, emailToUse, downloadUrl)
                    }
                } else {
                    _errorMessage.value = "Failed to upload image: ${task.exception?.message}"
                }
            }
        } catch (e: Exception) {
            Log.e("UserViewModel", "Error uploading image: ${e.message}")
            _errorMessage.value = "Error uploading image: ${e.message}"
            _isLoading.value = false
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    // Add a setter method to update the user data from outside
    fun updateUserDataValue(user: UserEntity?) {
        _userData.value = user
    }
    
    /**
     * Remove the user's profile photo and set it to default
     */
    fun removeProfilePhoto(name: String, email: String) {
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            try {
                // Check if user is authenticated
                val user = auth.currentUser ?: throw Exception("User not authenticated")
                val userId = user.uid
                
                // Get user's current profile photo URL
                val currentUser = userRepository.getUserData(userId)
                val currentPhotoUrl = currentUser?.profilePhotoUrl
                
                // Only attempt to delete if we have a URL and it's not a default avatar
                if (currentPhotoUrl != null && currentPhotoUrl.isNotEmpty() && !currentPhotoUrl.contains("default_avatar")) {
                    try {
                        // Delete old image from storage
                        val storageRef = storage.reference
                        
                        // Handle both https and gs URLs
                        val oldPhotoRef = if (currentPhotoUrl.startsWith("https://")) {
                            // Extract path from https URL
                            val path = currentPhotoUrl.substringAfter("o/").substringBefore("?")
                            // URL decode path
                            val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
                            storageRef.child(decodedPath)
                        } else if (currentPhotoUrl.startsWith("gs://")) {
                            // Extract path from gs URL
                            storage.getReferenceFromUrl(currentPhotoUrl)
                        } else {
                            // Not a valid URL to delete
                            null
                        }
                        
                        oldPhotoRef?.delete()?.await()
                        Log.d("UserViewModel", "Successfully deleted old profile photo")
                    } catch (e: Exception) {
                        Log.e("UserViewModel", "Error deleting old profile photo: ${e.message}")
                        // Continue with the update even if delete fails
                    }
                }
                
                // Update user's profile in Firebase Auth
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .setPhotoUri(null) // Remove photo URL
                    .build()
                
                user.updateProfile(profileUpdates).await()
                
                // Update user document with the updatedUser object
                val updatedUser = UserEntity(
                    uid = userId,
                    name = name,
                    email = email,
                    profilePhotoUrl = ""
                )
                userRepository.updateUser(updatedUser)
                
                // Clear cache to ensure fresh data
                userRepository.clearUserCache(userId)
                
                // Refresh user data in posts
                feedRepository.refreshUserDataInAllPosts(userId)
                
                // Send notification about profile update
                sendProfileUpdatedBroadcast(userId)
                
                // Set success state
                _updateSuccess.value = true
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error removing profile photo: ${e.message}")
                _errorMessage.value = "Failed to remove profile photo: ${e.message}"
                _isLoading.value = false
                _updateSuccess.value = false
            }
        }
    }
    
    // Helper function to send profile updated broadcast
    private fun sendProfileUpdatedBroadcast(userId: String) {
        // Create the intent that would be broadcast
        val intent = Intent("com.eaor.coffeefee.PROFILE_UPDATED")
        intent.putExtra("userId", userId)
        intent.putExtra("timestamp", System.currentTimeMillis())
        
        // Note: We can't actually send the broadcast without a context
        // This would normally be handled by the fragment or activity
        Log.d("UserViewModel", "Profile updated for user: $userId")
        
        // Set a flag to indicate profile was updated
        GlobalState.triggerRefreshAfterProfileChange(dataChanged = true)
    }
} 