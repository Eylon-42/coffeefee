package com.eaor.coffeefee

import android.app.Application
import com.eaor.coffeefee.data.AppDatabase

/**
 * Application class for Coffeefee
 * Provides global access to the database instance
 */
class CoffeefeeApplication : Application() {
    
    // Lazy initialized database instance
    val database by lazy {
        AppDatabase.getDatabase(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        // Initialize any application-wide components here
    }
} 