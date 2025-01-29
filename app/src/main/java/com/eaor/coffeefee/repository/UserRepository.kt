package com.eaor.coffeefee.repository

import android.util.Log
import com.eaor.coffeefee.data.User
import com.eaor.coffeefee.data.UserDao
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore
) {
    suspend fun getUserData(userId: String): User? {
        try {
            // First try to get from Room
            var user = userDao.getUserById(userId)
            
            // If not in Room, get from Firestore
            if (user == null) {
                val document = firestore.collection("Users")
                    .document(userId)
                    .get()
                    .await()

                if (document.exists()) {
                    user = User(
                        uid = userId,
                        name = document.getString("name") ?: "",
                        email = document.getString("email") ?: "",
                        profilePictureUrl = document.getString("profilePictureUrl")
                    )
                    // Cache in Room
                    try {
                        userDao.insertUser(user)
                        Log.d("UserRepository", "User cached in Room: $userId")
                    } catch (e: Exception) {
                        Log.e("UserRepository", "Error caching user: ${e.message}")
                    }
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
                "profilePictureUrl" to user.profilePictureUrl
            )
            
            firestore.collection("Users")
                .document(user.uid)
                .update(userData as Map<String, Any>)
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
}