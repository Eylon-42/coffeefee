package com.eaor.coffeefee.data

import androidx.room.*
import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

/**
 * UserDao interface for Room database access.
 * Provides both LiveData and Flow-based methods for reactive data access,
 * following MVVM architecture principles.
 */
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE uid = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("DELETE FROM users WHERE uid = :userId")
    suspend fun deleteUser(userId: String)
    
    // LiveData-based observation for backward compatibility
    @Query("SELECT * FROM users WHERE uid = :userId")
    fun observeUserByIdLiveData(userId: String): LiveData<UserEntity?>

    // Flow-based observation for modern coroutine support
    @Query("SELECT * FROM users WHERE uid = :userId")
    fun observeUserById(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users")
    fun observeAllUsers(): Flow<List<UserEntity>>

    /**
     * Get users by a list of IDs
     */
    @Query("SELECT * FROM users WHERE uid IN (:userIds)")
    suspend fun getUsersByIds(userIds: List<String>): List<UserEntity>
    
    /**
     * Get all users
     */
    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    /**
     * Delete a user by their ID
     */
    @Query("DELETE FROM users WHERE uid = :userId")
    suspend fun deleteUserById(userId: String)
}