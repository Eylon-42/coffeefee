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
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE uid = :userId")
    suspend fun getUserById(userId: String): User?

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("DELETE FROM users WHERE uid = :userId")
    suspend fun deleteUser(userId: String)
    
    // LiveData-based observation for backward compatibility
    @Query("SELECT * FROM users WHERE uid = :userId")
    fun observeUserByIdLiveData(userId: String): LiveData<User?>

    // Flow-based observation for modern coroutine support
    @Query("SELECT * FROM users WHERE uid = :userId")
    fun observeUserById(userId: String): Flow<User?>

    @Query("SELECT * FROM users")
    fun observeAllUsers(): Flow<List<User>>
}