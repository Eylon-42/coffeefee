package com.eaor.coffeefee.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.eaor.coffeefee.models.FeedItem

class PostDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "posts.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "posts"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USER_ID = "userId"
        private const val COLUMN_USER_NAME = "userName"
        private const val COLUMN_EXPERIENCE_DESCRIPTION = "experienceDescription"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_LIKE_COUNT = "likeCount"
        private const val COLUMN_COMMENT_COUNT = "commentCount"
        private const val COLUMN_LIKES = "likes"
        private const val COLUMN_PHOTO_URL = "photoUrl"
        private const val COLUMN_USER_PHOTO_URL = "userPhotoUrl"
        private const val COLUMN_LOCATION_NAME = "locationName"
        private const val COLUMN_LOCATION_LATITUDE = "locationLatitude"
        private const val COLUMN_LOCATION_LONGITUDE = "locationLongitude"
        private const val COLUMN_LOCATION_PLACE_ID = "locationPlaceId"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_USER_ID TEXT,
                $COLUMN_USER_NAME TEXT,
                $COLUMN_EXPERIENCE_DESCRIPTION TEXT,
                $COLUMN_TIMESTAMP INTEGER,
                $COLUMN_LIKE_COUNT INTEGER DEFAULT 0,
                $COLUMN_COMMENT_COUNT INTEGER DEFAULT 0,
                $COLUMN_LIKES TEXT,
                $COLUMN_PHOTO_URL TEXT,
                $COLUMN_USER_PHOTO_URL TEXT,
                $COLUMN_LOCATION_NAME TEXT,
                $COLUMN_LOCATION_LATITUDE REAL,
                $COLUMN_LOCATION_LONGITUDE REAL,
                $COLUMN_LOCATION_PLACE_ID TEXT
            )
        """.trimIndent()
        
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    // Method to insert a post
    fun insertPost(post: FeedItem) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ID, post.id)
            put(COLUMN_USER_ID, post.userId)
            put(COLUMN_USER_NAME, post.userName)
            put(COLUMN_EXPERIENCE_DESCRIPTION, post.experienceDescription)
            put(COLUMN_TIMESTAMP, post.timestamp)
            put(COLUMN_LIKE_COUNT, post.likeCount)
            put(COLUMN_COMMENT_COUNT, post.commentCount)
            put(COLUMN_LIKES, post.likes.joinToString(","))
            
            // Store photo URLs
            put(COLUMN_PHOTO_URL, post.photoUrl)
            put(COLUMN_USER_PHOTO_URL, post.userPhotoUrl)
            
            // Store location data if available
            if (post.location != null) {
                put(COLUMN_LOCATION_NAME, post.location.name)
                put(COLUMN_LOCATION_LATITUDE, post.location.latitude)
                put(COLUMN_LOCATION_LONGITUDE, post.location.longitude)
                put(COLUMN_LOCATION_PLACE_ID, post.location.placeId)
            }
        }
        
        // Use CONFLICT_REPLACE to update existing entries
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    // Method to retrieve all posts
    fun getAllPosts(): List<FeedItem> {
        val posts = mutableListOf<FeedItem>()
        val db = readableDatabase
        
        // Order by timestamp to show most recent first
        val cursor = db.query(
            TABLE_NAME,
            null, // Select all columns
            null, // No WHERE clause
            null, // No WHERE arguments
            null, // No GROUP BY
            null, // No HAVING
            "$COLUMN_TIMESTAMP DESC" // Order by timestamp descending
        )
        
        if (cursor.moveToFirst()) {
            do {
                // Create a location object if we have location data
                val locationName = if (cursor.getColumnIndex(COLUMN_LOCATION_NAME) >= 0) 
                    cursor.getString(cursor.getColumnIndex(COLUMN_LOCATION_NAME)) else null
                    
                val location = if (!locationName.isNullOrEmpty()) {
                    FeedItem.Location(
                        name = locationName,
                        latitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_LOCATION_LATITUDE)),
                        longitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_LOCATION_LONGITUDE)),
                        placeId = cursor.getString(cursor.getColumnIndex(COLUMN_LOCATION_PLACE_ID))
                    )
                } else null
                
                // Get photo URLs if they exist
                val photoUrl = if (cursor.getColumnIndex(COLUMN_PHOTO_URL) >= 0) 
                    cursor.getString(cursor.getColumnIndex(COLUMN_PHOTO_URL)) else null
                
                val userPhotoUrl = if (cursor.getColumnIndex(COLUMN_USER_PHOTO_URL) >= 0) 
                    cursor.getString(cursor.getColumnIndex(COLUMN_USER_PHOTO_URL)) else null
                
                // Handle empty likes string
                val likesString = cursor.getString(cursor.getColumnIndex(COLUMN_LIKES))
                val likes = if (likesString.isNullOrEmpty()) listOf() 
                            else likesString.split(",")
                
                val post = FeedItem(
                    id = cursor.getString(cursor.getColumnIndex(COLUMN_ID)),
                    userId = cursor.getString(cursor.getColumnIndex(COLUMN_USER_ID)),
                    userName = cursor.getString(cursor.getColumnIndex(COLUMN_USER_NAME)),
                    experienceDescription = cursor.getString(cursor.getColumnIndex(COLUMN_EXPERIENCE_DESCRIPTION)),
                    timestamp = cursor.getLong(cursor.getColumnIndex(COLUMN_TIMESTAMP)),
                    likeCount = cursor.getInt(cursor.getColumnIndex(COLUMN_LIKE_COUNT)),
                    commentCount = cursor.getInt(cursor.getColumnIndex(COLUMN_COMMENT_COUNT)),
                    likes = likes,
                    location = location,
                    photoUrl = photoUrl,
                    userPhotoUrl = userPhotoUrl
                )
                posts.add(post)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return posts
    }

    // Method to clear all posts
    fun clearPosts() {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_NAME")
        db.close()
    }
} 