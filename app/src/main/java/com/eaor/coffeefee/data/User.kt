package com.eaor.coffeefee.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Room entity representing a user.
 * Contains essential user information that needs to be cached locally.
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    @ColumnInfo(name = "uid")
    val uid: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "profile_photo_url")
    val profilePhotoUrl: String? = null,
    
    /**
     * Timestamp of when this user data was last updated from network
     * Used to determine when cache needs refreshing
     */
    @ColumnInfo(name = "last_updated_timestamp")
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)