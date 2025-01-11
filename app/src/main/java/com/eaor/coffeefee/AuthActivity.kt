package com.eaor.coffeefee

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import com.eaor.coffeefee.fragments.SignInFragment

class AuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth) // Create layout for AuthActivity

        // Initially load the login fragment
        if (savedInstanceState == null) {
            val loginFragment = SignInFragment()// Load your SignInFragment first
            supportFragmentManager.beginTransaction()
                .replace(R.id.auth_fragment_container, loginFragment)
                .commit()
        }
    }

}
