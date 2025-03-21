package com.eaor.coffeefee

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.eaor.coffeefee.R
import com.eaor.coffeefee.viewmodels.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

class AuthActivity : AppCompatActivity() {
    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        
        // Get current user from ViewModel
        viewModel.getCurrentUser()
        
        // Observe current user state
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                // User is signed in, navigate to MainActivity
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return@observe
            } else {
                // User is not signed in, show auth UI
                if (savedInstanceState == null) {
                    setContentView(R.layout.activity_auth)
                    
                    // Set up Navigation
                    val navHostFragment = supportFragmentManager
                        .findFragmentById(R.id.auth_nav_host_fragment) as NavHostFragment
                    val navController = navHostFragment.navController
                }
            }
        }
    }
}
