package com.eaor.coffeefee.repositories

import android.util.Log
import com.eaor.coffeefee.data.UserEntity
import com.eaor.coffeefee.data.UserDao
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import com.google.firebase.firestore.FieldPath
import com.google.firebase.auth.FirebaseAuth
import com.eaor.coffeefee.models.User
import com.eaor.coffeefee.models.toMap
import com.eaor.coffeefee.models.toUser

/**
 * UserRepository follows the Repository pattern from MVVM architecture.
 * It provides a clean API for data access and abstracts the data sources
 * (Firestore and Room database) from the rest of the app.
 */
class UserRepository(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore
) {
    /**
     * Gets user data, with improved caching logic.
     * 
     * @param userId The user ID to get data for
     * @param forceRefresh If true, bypass cache and fetch directly from Firestore
     * @param maxAgeMinutes Maximum age of cached data in minutes before refreshing (default: 60)
     * @return UserEntity object or null if not found
     */
    suspend fun getUserData(userId: String, forceRefresh: Boolean = false, maxAgeMinutes: Int = 60): UserEntity? = withContext(Dispatchers.IO) {
        if (userId.isEmpty()) return@withContext null
        
        try {
            val cachedUser = if (!forceRefresh) userDao.getUserById(userId) else null
            val currentTimeMillis = System.currentTimeMillis()
            
            // Check if we have a valid cached user
            val shouldUseCache = cachedUser != null && 
                                !cachedUser.name.isNullOrEmpty() &&
                                !forceRefresh &&
                                // Check if the cached data is fresh enough
                                (currentTimeMillis - cachedUser.lastUpdatedTimestamp) < maxAgeMinutes * 60 * 1000
            
            if (shouldUseCache) {
                return@withContext cachedUser
            }
            
            // If not in cache or force refresh, fetch from Firestore
            val userDocument = firestore.collection("Users").document(userId).get().await()
            
            if (userDocument.exists()) {
                val name = userDocument.getString("name")
                if (name.isNullOrEmpty()) {
                    Log.e("UserRepository", "User exists but has empty name in Firestore: $userId")
                    
                    // If Firestore has no name but we have a cached name, keep using the cache
                    if (cachedUser != null && !cachedUser.name.isNullOrEmpty()) {
                        return@withContext cachedUser
                    }
                    
                    return@withContext null
                }
                
                val email = userDocument.getString("email") ?: ""
                // Try multiple fields for profile photo URL
                val profileUrl = userDocument.getString("profilePhotoUrl") 
                    ?: userDocument.getString("profilePictureUrl")
                    ?: userDocument.getString("photoUrl")
                    ?: ""
                
                // Create user object with current timestamp
                val user = UserEntity(
                    uid = userId,
                    name = name,
                    email = email,
                    profilePhotoUrl = profileUrl,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
                
                // Compare with cached data to see if anything changed
                val hasChanged = cachedUser == null || 
                                 cachedUser.name != name || 
                                 cachedUser.email != email || 
                                 cachedUser.profilePhotoUrl != profileUrl
                
                // Only invalidate Picasso cache if profile URL changed
                if ((hasChanged || forceRefresh) && profileUrl.isNotEmpty()) {
                    try {
                        // Invalidate regardless of cache state when forceRefresh is true
                        com.squareup.picasso.Picasso.get().invalidate(profileUrl)
                    } catch (e: Exception) {
                        Log.e("UserRepository", "Error invalidating Picasso cache: ${e.message}")
                    }
                }
                
                // Save to Room database
                userDao.insertUser(user)
                
                return@withContext user
            } else {
                Log.e("UserRepository", "User not found in Firestore: $userId")
                
                // If not in Firestore but we have cached data, return that rather than null
                if (cachedUser != null) {
                    return@withContext cachedUser
                }
                
                return@withContext null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            
            Log.e("UserRepository", "Error fetching user data: ${e.message}")
            
            // If there's an error fetching, return cached data if available
            if (!forceRefresh) {
                try {
                    val cachedUser = userDao.getUserById(userId)
                    if (cachedUser != null) {
                        return@withContext cachedUser
                    }
                } catch (e2: Exception) {
                    Log.e("UserRepository", "Error getting cached user after fetch error: ${e2.message}")
                }
            }
            
            return@withContext null
        }
    }

    suspend fun updateUser(user: UserEntity): Boolean {
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
    
    // Get user from local cache without hitting Firestore
    suspend fun getUserFromLocalCache(userId: String): UserEntity? {
        return try {
            val user = userDao.getUserById(userId)
            if (user != null) {
                Log.d("UserRepository", "Found user in local cache: $userId")
            }
            user
        } catch (e: Exception) {
            Log.e("UserRepository", "Error getting user from local cache: ${e.message}")
            null
        }
    }
    
    // Cache user locally without hitting Firestore
    suspend fun cacheUserLocally(userId: String, name: String, email: String?, profilePhotoUrl: String?) {
        try {
            val user = UserEntity(
                uid = userId,
                name = name,
                email = email ?: "",
                profilePhotoUrl = profilePhotoUrl
            )
            userDao.insertUser(user)
            Log.d("UserRepository", "Cached user locally: $userId")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error caching user locally: ${e.message}")
        }
    }

    suspend fun getUsersByIds(userIds: List<String>): List<UserEntity> {
        return try {
            userDao.getUsersByIds(userIds)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error getting users by IDs: ${e.message}")
            emptyList()
        }
    }

    /**
     * Refresh users from Firestore and update Room database
     * @return The number of users that were updated
     */
    suspend fun refreshUsers(): Int = withContext(Dispatchers.IO) {
        try {
            var updatedCount = 0
            val users = userDao.getAllUsers()
            val userIds = users.map { it.uid }
            
            // Only fetch users we have in our database
            if (userIds.isNotEmpty()) {
                val usersCollection = firestore.collection("Users")
                
                // Process users in batches if there are many
                val batchSize = 10
                userIds.chunked(batchSize).forEach { batch ->
                    try {
                        val query = usersCollection.whereIn(FieldPath.documentId(), batch)
                        val snapshots = query.get().await()

                        for (document in snapshots.documents) {
                            val userId = document.id
                            val name = document.getString("name") ?: continue
                            val email = document.getString("email") ?: ""
                            val profileUrl = document.getString("profilePhotoUrl") 
                                ?: document.getString("profilePictureUrl")
                                ?: document.getString("photoUrl")
                                ?: ""
                            
                            // Find existing user in our room database
                            val existingUser = users.find { it.uid == userId }
                            
                            // Only update if something changed
                            if (existingUser == null || 
                                existingUser.name != name || 
                                existingUser.email != email || 
                                existingUser.profilePhotoUrl != profileUrl) {
                                
                                val user = UserEntity(
                                    uid = userId,
                                    name = name,
                                    email = email,
                                    profilePhotoUrl = profileUrl
                                )
                                
                                userDao.insertUser(user)
                                updatedCount++
                                
                                // Invalidate Picasso's cache for this user's photo
                                if (profileUrl.isNotEmpty()) {
                                    try {
                                        com.squareup.picasso.Picasso.get().invalidate(profileUrl)
                                    } catch (e: Exception) {
                                        Log.e("UserRepository", "Error invalidating Picasso cache: ${e.message}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("UserRepository", "Error fetching user batch: ${e.message}")
                    }
                }
            }
            
            return@withContext updatedCount
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("UserRepository", "Error refreshing users: ${e.message}")
            return@withContext 0
        }
    }

    /**
     * Clear a specific user's cache from Room database
     */
    suspend fun clearUserCache(userId: String) {
        try {
            userDao.deleteUserById(userId)
            Log.d("UserRepository", "Cleared cache for user: $userId")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error clearing user cache: ${e.message}")
        }
    }

    /**
     * Get a map of users by their IDs, with improved handling of force refresh
     * @param userIds List of user IDs to fetch
     * @param forceRefresh Whether to force refresh from Firestore
     * @return Map of userId to UserEntity objects
     */
    suspend fun getUsersMapByIds(userIds: List<String>, forceRefresh: Boolean = false): Map<String, UserEntity> = withContext(Dispatchers.IO) {
        if (userIds.isEmpty()) return@withContext emptyMap<String, UserEntity>()
        
        Log.d("UserRepository", "Getting user data for ${userIds.size} users, forceRefresh=$forceRefresh")
        
        try {
            // If we're force refreshing, first clean the local cache for these users
            if (forceRefresh) {
                Log.d("UserRepository", "Force refresh requested, clearing local cache for selected users")
                for (userId in userIds) {
                    try {
                        clearUserCache(userId)
                    } catch (e: Exception) {
                        Log.e("UserRepository", "Error clearing cache for user $userId: ${e.message}")
                    }
                }
            }
            
            // First try to get all users from Room unless force refresh is requested
            val cachedUsers = if (!forceRefresh) userDao.getUsersByIds(userIds) else emptyList()
            val resultMap = cachedUsers.associateBy { it.uid }.toMutableMap()
            
            // Find user IDs that weren't in the cache
            val missingUserIds = userIds.filter { userId -> !resultMap.containsKey(userId) }
            
            // Any users with empty names are considered incomplete and should be refreshed
            val incompleteUserIds = resultMap.filter { it.value.name.isNullOrEmpty() }.keys.toList()
            
            // When force refreshing, we also include all IDs, not just missing or incomplete ones
            val userIdsToFetch = if (forceRefresh) {
                userIds
            } else {
                (missingUserIds + incompleteUserIds).distinct()
            }
            
            if (userIdsToFetch.isNotEmpty()) {
                Log.d("UserRepository", "Fetching ${userIdsToFetch.size} users from Firestore" +
                    if (forceRefresh) " (forced refresh)" else "")
                
                // Track successfully fetched users to retry any failures with lowercase collection
                val fetchedUserIds = mutableSetOf<String>()
                
                // Process in batches to avoid exceeding Firestore limits
                val batchSize = 10
                userIdsToFetch.chunked(batchSize).forEach { batch ->
                    try {
                        // Try both "Users" (uppercase) and "users" (lowercase) collections
                        val usersCollection = firestore.collection("Users")
                        
                        // First try uppercase collection
                        val uppercaseQuery = usersCollection.whereIn(FieldPath.documentId(), batch)
                        val uppercaseSnapshots = uppercaseQuery.get().await()
                        
                        for (document in uppercaseSnapshots.documents) {
                            val userId = document.id
                            val name = document.getString("name")
                            
                            if (!name.isNullOrEmpty()) {
                                val profileUrl = document.getString("profilePhotoUrl")
                                    ?: document.getString("profilePictureUrl")
                                    ?: document.getString("photoUrl")
                                
                                val email = document.getString("email") ?: ""
                                
                                val user = UserEntity(
                                    uid = userId,
                                    name = name,
                                    email = email,
                                    profilePhotoUrl = profileUrl
                                )
                                
                                // Add to result map and cache in Room
                                resultMap[userId] = user
                                userDao.insertUser(user)
                                fetchedUserIds.add(userId)
                                
                                // Invalidate Picasso cache for this URL immediately
                                if (!profileUrl.isNullOrEmpty()) {
                                    try {
                                        com.squareup.picasso.Picasso.get().invalidate(profileUrl)
                                    } catch (e: Exception) {
                                        Log.e("UserRepository", "Error invalidating profile image: ${e.message}")
                                    }
                                }
                                
                                Log.d("UserRepository", "Updated user from Firestore: $name (ID: $userId)")
                            }
                        }
                        
                        // Find which users are still missing after trying uppercase collection
                        val remainingUserIds = batch.filter { userId -> 
                            !fetchedUserIds.contains(userId) 
                        }
                        
                        // If any users are still missing, try lowercase collection
                        if (remainingUserIds.isNotEmpty()) {
                            val usersLowercaseCollection = firestore.collection("users")
                            val lowercaseQuery = usersLowercaseCollection.whereIn(FieldPath.documentId(), remainingUserIds)
                            val lowercaseSnapshots = lowercaseQuery.get().await()
                            
                            for (document in lowercaseSnapshots.documents) {
                                val userId = document.id
                                val name = document.getString("name")
                                
                                if (!name.isNullOrEmpty()) {
                                    val profileUrl = document.getString("profilePhotoUrl")
                                        ?: document.getString("profilePictureUrl")
                                        ?: document.getString("photoUrl")
                                    
                                    val email = document.getString("email") ?: ""
                                    
                                    val user = UserEntity(
                                        uid = userId,
                                        name = name,
                                        email = email,
                                        profilePhotoUrl = profileUrl
                                    )
                                    
                                    // Add to result map and cache in Room
                                    resultMap[userId] = user
                                    userDao.insertUser(user)
                                    fetchedUserIds.add(userId)
                                    
                                    // Invalidate Picasso cache for this URL immediately
                                    if (!profileUrl.isNullOrEmpty()) {
                                        try {
                                            com.squareup.picasso.Picasso.get().invalidate(profileUrl)
                                        } catch (e: Exception) {
                                            Log.e("UserRepository", "Error invalidating profile image: ${e.message}")
                                        }
                                    }
                                    
                                    Log.d("UserRepository", "Updated user from lowercase collection: $name (ID: $userId)")
                                }
                            }
                        }
                        
                        // Try to get users from firebase auth as a last resort for the current user
                        val auth = FirebaseAuth.getInstance()
                        val currentUser = auth.currentUser
                        
                        if (currentUser != null) {
                            // Check if current user is in the remaining missing users
                            val isCurrentUserMissing = batch.contains(currentUser.uid) && !fetchedUserIds.contains(currentUser.uid)
                            
                            if (isCurrentUserMissing && !currentUser.displayName.isNullOrEmpty()) {
                                val user = UserEntity(
                                    uid = currentUser.uid,
                                    name = currentUser.displayName ?: "User",
                                    email = currentUser.email ?: "",
                                    profilePhotoUrl = currentUser.photoUrl?.toString()
                                )
                                
                                // Add to result map and cache in Room
                                resultMap[currentUser.uid] = user
                                userDao.insertUser(user)
                                fetchedUserIds.add(currentUser.uid)
                                
                                Log.d("UserRepository", "Used Firebase Auth for current user: ${user.name}")
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("UserRepository", "Error fetching batch of users: ${e.message}")
                        // Continue with next batch even if one fails
                    }
                }
                
                // Log any users we couldn't find
                val missingAfterFetch = userIdsToFetch.filter { !fetchedUserIds.contains(it) }
                if (missingAfterFetch.isNotEmpty()) {
                    Log.w("UserRepository", "Could not find ${missingAfterFetch.size} users: $missingAfterFetch")
                }
            }
            
            return@withContext resultMap
            
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("UserRepository", "Error getting users map: ${e.message}")
            return@withContext emptyMap<String, UserEntity>()
        }
    }

    /**
     * Update specific fields of a user.
     * This is useful when you only want to update certain fields and not have a complete UserEntity object.
     */
    suspend fun updateUserData(userId: String, name: String, email: String? = null, profilePhotoUrl: String? = null): Boolean {
        return try {
            Log.d("UserRepository", "Updating user fields: $userId, name=$name, photo=${profilePhotoUrl?.take(20)}")
            
            // First get existing user to make sure we don't lose data
            val existingUser = getUserFromLocalCache(userId)
            
            // Create update data with only fields that are provided
            val userData = HashMap<String, Any>()
            userData["name"] = name
            
            if (email != null) {
                userData["email"] = email
            }
            
            if (profilePhotoUrl != null) {
                userData["profilePhotoUrl"] = profilePhotoUrl
            }

            // Update Firestore
            firestore.collection("Users")
                .document(userId)
                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                .await()

            // Update Room database
            // If we had an existing user, update its fields, otherwise create a new one
            val updatedUser = if (existingUser != null) {
                UserEntity(
                    uid = userId,
                    name = name,
                    email = email ?: existingUser.email,
                    profilePhotoUrl = profilePhotoUrl ?: existingUser.profilePhotoUrl
                )
            } else {
                UserEntity(
                    uid = userId,
                    name = name,
                    email = email ?: "",
                    profilePhotoUrl = profilePhotoUrl
                )
            }
            
            userDao.insertUser(updatedUser)
            Log.d("UserRepository", "User fields updated successfully: $userId")
            
            // Invalidate Picasso cache for this user's photo if it changed
            if (profilePhotoUrl != null && profilePhotoUrl.isNotEmpty() && 
                profilePhotoUrl != existingUser?.profilePhotoUrl) {
                try {
                    // Using ImageLoader utility instead of direct Picasso cache invalidation
                    Log.d("UserRepository", "Profile photo URL changed from ${existingUser?.profilePhotoUrl} to $profilePhotoUrl")
                } catch (e: Exception) {
                    Log.e("UserRepository", "Error handling profile photo URL change: ${e.message}")
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e("UserRepository", "Error updating user fields: ${e.message}")
            false
        }
    }

    /**
     * Get complete user data from Firestore including preferences and tags
     * This uses the User model from the models package that matches the full Firestore schema
     */
    suspend fun getCompleteUserData(userId: String): com.eaor.coffeefee.models.User? = withContext(Dispatchers.IO) {
        try {
            if (userId.isEmpty()) return@withContext null
            
            // Get user document from Firestore
            val userDoc = firestore.collection("Users").document(userId).get().await()
            
            if (userDoc.exists()) {
                // Convert document data to User model
                val userData = userDoc.data ?: return@withContext null
                val user = userData.toUser(userId)
                
                Log.d("UserRepository", "Got complete user data for $userId: name=${user.name}, tags=${user.tags.size}")
                return@withContext user
            } else {
                Log.d("UserRepository", "User document for $userId not found")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Error getting complete user data: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Update complete user data including preferences and tags
     */
    suspend fun updateCompleteUser(user: User): Boolean = withContext(Dispatchers.IO) {
        try {
            // Convert User to Map for Firestore
            val userData = user.toMap()
            
            // Update in Firestore
            firestore.collection("Users")
                .document(user.uid)
                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                .await()
            
            // Also update the basic user in Room for caching
            val basicUser = UserEntity(
                uid = user.uid,
                name = user.name,
                email = user.email,
                profilePhotoUrl = user.profilePhotoUrl
            )
            userDao.insertUser(basicUser)
            
            Log.d("UserRepository", "Updated complete user data for ${user.uid}")
            return@withContext true
        } catch (e: Exception) {
            Log.e("UserRepository", "Error updating complete user data: ${e.message}")
            return@withContext false
        }
    }
}