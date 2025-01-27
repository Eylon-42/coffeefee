package com.eaor.coffeefee.repository

import com.eaor.coffeefee.data.User
import com.eaor.coffeefee.data.UserDao
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore
) {
    suspend fun getUserData(uid: String): User? {
        // First, try to get from cache
        var user = userDao.getUserById(uid)
        
        // If not in cache, fetch from Firestore
        if (user == null) {
            try {
                val document = firestore.collection("Users")
                    .document(uid)
                    .get()
                    .await()

                if (document.exists()) {
                    user = User(
                        uid = uid,
                        name = document.getString("name") ?: "",
                        email = document.getString("email") ?: ""
                    )
                    // Cache the user data
                    userDao.insertUser(user)
                }
            } catch (e: Exception) {
                // Handle Firestore fetch error
                e.printStackTrace()
            }
        }
        
        return user
    }

    suspend fun updateCache(user: User) {
        userDao.insertUser(user)
    }

    suspend fun clearCache(uid: String) {
        userDao.deleteUser(uid)
    }
}