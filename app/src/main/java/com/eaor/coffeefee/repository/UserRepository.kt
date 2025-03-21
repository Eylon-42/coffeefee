package com.eaor.coffeefee.repository

import android.util.Log
import com.eaor.coffeefee.data.User
import com.eaor.coffeefee.data.UserDao
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * UserRepository follows the Repository pattern from MVVM architecture.
 * It provides a clean API for data access and abstracts the data sources
 * (Firestore and Room database) from the rest of the app.
 */
class UserRepository(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore
) {
    suspend fun getUserData(userId: String, forceRefresh: Boolean = false): User? {
        try {
            // First check Room unless a forced refresh is requested
            var user = if (!forceRefresh) userDao.getUserById(userId) else null
            
            // Log what we're doing to help with debugging
            if (user != null && !forceRefresh) {
                Log.d("UserRepository", "Using cached user data from Room for $userId")
            } else {
                Log.d("UserRepository", "Fetching fresh user data from Firebase for $userId")
            }

            // If not in Room or forcing refresh, get from Firestore
            if (user == null || forceRefresh) {
                val document = firestore.collection("Users")
                    .document(userId)
                    .get()
                    .await()

                if (document.exists()) {
                    // Try to get profile photo URL from different possible fields
                    val profileUrl = document.getString("profilePhotoUrl")
                        ?: document.getString("profilePictureUrl")
                        ?: document.getString("photoUrl")
                        
                    user = User(
                        uid = userId,
                        name = document.getString("name") ?: "",
                        email = document.getString("email") ?: "",
                        profilePhotoUrl = profileUrl
                    )
                    // Cache in Room - even on force refresh we update the cache
                    try {
                        userDao.insertUser(user)
                        Log.d("UserRepository", "Updated user in Room cache: $userId")
                    } catch (e: Exception) {
                        Log.e("UserRepository", "Error caching user: ${e.message}")
                    }
                } else {
                    Log.d("UserRepository", "User document not found in Firestore: $userId")
                }
            }
            return user

        } catch (e: Exception) {
            Log.e("UserRepository", "Error getting user data: ${e.message}")
            return null
        }
    }

    suspend fun updateUser(user: User): Boolean {
        return try {
            // Update Firestore first
            val userData = hashMapOf(
                "name" to user.name,
                "email" to user.email,
                "profilePhotoUrl" to user.profilePhotoUrl
            )

            Log.d("UserRepository", "Updating user in Firestore: ${user.uid} with name=${user.name}, email=${user.email}")
            
            // Use set with merge option instead of update
            firestore.collection("Users")
                .document(user.uid)
                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                .await()

            // If Firestore update successful, update Room
            userDao.insertUser(user)
            Log.d("UserRepository", "User updated successfully: ${user.uid}")
            true
        } catch (e: Exception) {
            Log.e("UserRepository", "Error updating user: ${e.message}")
            false
        }
    }

    suspend fun clearCache() {
        try {
            userDao.deleteAllUsers()
            Log.d("UserRepository", "Cache cleared successfully")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error clearing cache: ${e.message}")
        }
    }

    // Add new method to observe user data changes with LiveData
    fun observeUserByIdLiveData(userId: String) = userDao.observeUserByIdLiveData(userId)

    // Method to get user Flows for reactive programming
    fun observeUserById(userId: String) = userDao.observeUserById(userId)
}