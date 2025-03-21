package com.eaor.coffeefee.viewmodels

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eaor.coffeefee.models.FeedItem
import com.eaor.coffeefee.repository.FeedRepository
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import android.util.Log

class ProfileViewModel : BaseViewModel() {
    private val storage = FirebaseStorage.getInstance()
    
    private val _userData = MutableLiveData<Map<String, Any>>()
    val userData: LiveData<Map<String, Any>> = _userData
    
    private val _userPosts = MutableLiveData<List<FeedItem>>()
    val userPosts: LiveData<List<FeedItem>> = _userPosts
    
    private lateinit var feedRepository: FeedRepository
    
    // Set the repository (will be called from fragment)
    fun setRepository(repository: FeedRepository) {
        feedRepository = repository
    }

    fun loadUserData(userId: String? = null) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    setError("User not authenticated")
                    setLoading(false)
                    return@launch
                }
                
                // Focus only on the Users collection with capital U
                val userDoc = db.collection("Users").document(currentUser.uid).get().await()
                if (userDoc.exists()) {
                    _userData.value = userDoc.data ?: mapOf()
                } else {
                    // If not found, use local auth data as fallback
                    _userData.value = mapOf(
                        "userId" to currentUser.uid,
                        "name" to (currentUser.displayName ?: ""),
                        "email" to (currentUser.email ?: ""),
                        "profilePhotoUrl" to (currentUser.photoUrl?.toString() ?: "")
                    )
                }
                setLoading(false)
            } catch (e: Exception) {
                setError("Error loading user data: ${e.message}")
                setLoading(false)
            }
        }
    }
    
    fun loadUserPosts(userId: String? = null) {
        setLoading(true)
        
        Log.d("ProfileViewModel", "Starting to load user posts")
        
        viewModelScope.launch {
            try {
                val targetUserId = userId ?: auth.currentUser?.uid
                if (targetUserId == null) {
                    setError("User not authenticated")
                    setLoading(false)
                    Log.e("ProfileViewModel", "User not authenticated")
                    return@launch
                }
                
                Log.d("ProfileViewModel", "Looking for posts with userId: $targetUserId")
                
                // First try to get posts from local database
                var posts = feedRepository.getFeedItemsByUserId(targetUserId)
                
                // If no local results, fetch from Firestore
                if (posts.isEmpty()) {
                    Log.d("ProfileViewModel", "No posts found in Room, fetching from Firestore")
                    posts = feedRepository.loadUserPosts(targetUserId)
                }
                
                Log.d("ProfileViewModel", "Final post count: ${posts.size}")
                _userPosts.value = posts
                setLoading(false)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading user posts: ${e.message}", e)
                setError("Error loading user posts: ${e.message}")
                setLoading(false)
            }
        }
    }
    
    fun updateProfile(name: String, imageUri: Uri?) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                if (user == null) {
                    setError("User not authenticated")
                    setLoading(false)
                    return@launch
                }
                
                // Upload new profile image if provided
                val profilePhotoUrl = if (imageUri != null) {
                    val photoRef = storage.reference.child("profiles/${user.uid}/${UUID.randomUUID()}.jpg")
                    photoRef.putFile(imageUri).await()
                    photoRef.downloadUrl.await().toString()
                } else {
                    user.photoUrl?.toString() ?: ""
                }
                
                // Update user profile in Auth
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .setPhotoUri(if (profilePhotoUrl.isNotEmpty()) Uri.parse(profilePhotoUrl) else null)
                    .build()
                
                user.updateProfile(profileUpdates).await()
                
                // Update user data in Firestore - use Users collection with capital U
                val userData = mapOf(
                    "name" to name,
                    "email" to (user.email ?: ""),
                    "profilePhotoUrl" to profilePhotoUrl  // Use consistent field name
                )
                
                db.collection("Users").document(user.uid)
                    .set(userData, com.google.firebase.firestore.SetOptions.merge())
                    .await()
                
                // Update local data
                loadUserData()
                
                // Update posts with new user data
                feedRepository.updateUserDataInPosts(
                    userId = user.uid,
                    userName = name,
                    profilePhotoUrl = profilePhotoUrl
                )
                
                // Reload user posts to reflect the updated user data
                loadUserPosts()
                
                setLoading(false)
            } catch (e: Exception) {
                setError("Error updating profile: ${e.message}")
                setLoading(false)
            }
        }
    }
    
    fun deletePost(postId: String) {
        setLoading(true)
        
        viewModelScope.launch {
            try {
                // Delete from Firestore
                db.collection("Posts").document(postId).delete().await()
                
                // Delete from local database
                feedRepository.deleteFeedItem(postId)
                
                // Update local posts list
                val currentPosts = _userPosts.value?.toMutableList() ?: mutableListOf()
                val updatedPosts = currentPosts.filter { it.id != postId }
                _userPosts.value = updatedPosts
                
                setLoading(false)
            } catch (e: Exception) {
                setError("Error deleting post: ${e.message}")
                setLoading(false)
            }
        }
    }
} 