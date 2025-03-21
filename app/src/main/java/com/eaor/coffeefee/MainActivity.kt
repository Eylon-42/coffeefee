package com.eaor.coffeefee

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.eaor.coffeefee.fragments.FeedFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.fragment.app.FragmentManager

class MainActivity : AppCompatActivity() {
    companion object {
        lateinit var placesClient: PlacesClient
        private const val CURRENT_TAB_KEY = "currentTabKey"
        private const val TAG = "MainActivity"
    }
    
    private lateinit var navController: NavController
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var navGraph: NavGraph
    
    // Track which tab is currently active
    private var currentTabId = R.id.feedFragment
    
    // Define the tab fragments
    private val tabDestinations = setOf(
        R.id.feedFragment,
        R.id.searchFragment,
        R.id.favoriteFragment,
        R.id.profileTab
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Restore saved state
        if (savedInstanceState != null) {
            currentTabId = savedInstanceState.getInt(CURRENT_TAB_KEY, R.id.feedFragment)
        }

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

        setupNavigation()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current tab
        outState.putInt(CURRENT_TAB_KEY, currentTabId)
        
        // Remove the call to saveFragmentInstanceState which might be causing issues
        // Fragment state will be automatically saved by the FragmentManager
    }

    private fun setupNavigation() {
        // Find views
        navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navGraph = navController.graph
        
        bottomNav = findViewById(R.id.bottom_nav)
        
        // Setup with NavigationUI to get automatic integration with the framework
        NavigationUI.setupWithNavController(bottomNav, navController)
        
        // Also set up custom bottom navigation item click listener to properly handle state
        bottomNav.setOnItemSelectedListener { item ->
            handleBottomNavItemSelected(item.itemId)
        }
        
        // Ensure the initial tab is displayed
        if (currentTabId != 0) {
            bottomNav.selectedItemId = currentTabId
        }
        
        // Monitor destination changes for logging
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d("MainActivity", "Destination changed to: ${destination.id}")
            
            // If this is a main tab, update the current tab
            if (destination.id in tabDestinations) {
                currentTabId = destination.id
                Log.d("MainActivity", "Updated current tab to: $currentTabId")
                
                // Update the UI to reflect the current tab
                if (bottomNav.selectedItemId != destination.id) {
                    bottomNav.selectedItemId = destination.id
                }
            }
        }
        
        // Setup custom back button handling
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Check if we're on a nested screen (not a main tab)
                val currentDestId = navController.currentDestination?.id
                if (currentDestId != null && currentDestId !in tabDestinations) {
                    // Before navigating back, store the current destination id
                    val previousDestId = currentDestId
                    
                    // Let the nav controller handle normal back button behavior
                    val success = navController.navigateUp()
                    
                    // After navigation, check if we landed on a main tab
                    if (success) {
                        val newDestId = navController.currentDestination?.id
                        if (newDestId != null && newDestId in tabDestinations) {
                            // Update the bottom nav selection to match the current destination
                            Log.d("MainActivity", "Back navigation: from $previousDestId to main tab $newDestId")
                            bottomNav.selectedItemId = newDestId
                            currentTabId = newDestId
                        }
                    }
                } else if (!navController.popBackStack()) {
                    // If we're already at a main tab and can't pop back, finish the activity
                    finish()
                }
            }
        })
    }

    /**
     * Handle bottom navigation item selection with proper navigation state management
     */
    private fun handleBottomNavItemSelected(itemId: Int): Boolean {
        // Skip if this is the same tab and we're already at the top level
        if (itemId == currentTabId && navController.currentDestination?.id == itemId) {
            // If we're already on this tab, check if we need to pop back to the root
            // This handles the case where we're in a nested fragment of the current tab
            val currentBackStackEntry = navController.currentBackStackEntry
            val startDestination = navController.graph.startDestinationId
            
            if (currentBackStackEntry != null && currentBackStackEntry.destination.id != itemId) {
                // We're in a nested fragment, so pop back to the root tab fragment
                Log.d("MainActivity", "Already on tab $itemId but in a nested fragment - popping to root")
                navController.popBackStack(itemId, false)
                return true
            }
            
            return true
        }

        // Check if we're navigating to a different tab
        if (itemId != currentTabId) {
            Log.d("MainActivity", "Switching from tab ${currentTabId} to $itemId")
        }

        // Update current tab ID
        currentTabId = itemId
        
        // Create navigation options for smooth navigation between tabs
        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(navController.graph.startDestinationId, false)
            .build()
        
        // Navigate to the selected tab
        return try {
            navController.navigate(itemId, null, navOptions)
            true
        } catch (e: Exception) {
            Log.e("MainActivity", "Navigation error: ${e.message}")
            false
        }
    }

    // Add a helper method to handle navigation from child fragments
    fun navigateToTopLevelDestination(destinationId: Int) {
        if (destinationId in tabDestinations) {
            // Set the selected item in the bottom nav if needed
            bottomNav.selectedItemId = destinationId
        }
    }

    /**
     * Updates the comment count for a post in the feed
     */
    fun updateCommentCount(postId: String, count: Int) {
        Log.d(TAG, "Updating comment count for post $postId to $count")
        // Find the fragment that manages the feed
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val feedFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull { it is FeedFragment } as? FeedFragment
        
        // Update the comment count in the feed fragment
        feedFragment?.updateCommentCount(postId, count)
    }
}