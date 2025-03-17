package com.eaor.coffeefee

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.ai.client.generativeai.GenerativeModel

class MainActivity : AppCompatActivity() {
    companion object {
        lateinit var placesClient: PlacesClient
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            // Initialize Places SDK
            val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
            if (apiKey.isEmpty()) {
                throw IllegalStateException("No API key found")
            }

            if (!Places.isInitialized()) {
                Places.initialize(applicationContext, apiKey)
                placesClient = Places.createClient(this)
                Log.d("Places SDK", "Successfully initialized Places SDK")
            }
        } catch (e: Exception) {
            Log.e("Places SDK", "Error initializing Places: ${e.message}")
            Toast.makeText(this, "Error initializing Places SDK", Toast.LENGTH_LONG).show()
        }

        // Get NavHostFragment and NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)
    }
}