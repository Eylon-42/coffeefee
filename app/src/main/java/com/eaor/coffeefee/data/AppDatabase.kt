package com.eaor.coffeefee.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UserEntity::class, 
        FeedItemEntity::class, 
        CommentEntity::class,
        CoffeeShopEntity::class
    ], 
    version = 11, 
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun feedItemDao(): FeedItemDao
    abstract fun commentDao(): CommentDao
    abstract fun coffeeShopDao(): CoffeeShopDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "coffeefee_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Alias for getInstance for code consistency
        fun getInstance(context: Context): AppDatabase {
            return getDatabase(context)
        }

        fun clearDatabase(context: Context) {
            context.deleteDatabase("coffeefee_database")
            INSTANCE = null
        }
    }
}