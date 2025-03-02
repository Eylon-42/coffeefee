package com.eaor.coffeefee.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.eaor.coffeefee.MainActivity
import com.eaor.coffeefee.R
import com.google.firebase.auth.FirebaseAuth

class SignInFragment : Fragment() {
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var signInButton: Button
    private lateinit var registerButton: Button
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_log_in, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize views
        emailEditText = view.findViewById(R.id.etEmail)
        passwordEditText = view.findViewById(R.id.etPassword)
        signInButton = view.findViewById(R.id.btnSignIn)
        registerButton = view.findViewById(R.id.tvRegister)

        // Set click listeners
        signInButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (validateInput(email, password)) {
                signInWithEmailPassword(email, password)
            }
        }

        registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_signInFragment_to_registerFragment)
        }
    }

    private fun signInWithEmailPassword(email: String, password: String) {
        signInButton.isEnabled = false // Disable button during authentication

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Sign in success, navigate to MainActivity
                    val intent = Intent(activity, MainActivity::class.java)
                    startActivity(intent)
                    activity?.finish()
                } else {
                    // If sign in fails, display a message to the user
                    Toast.makeText(
                        context,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    signInButton.isEnabled = true
                }
            }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            emailEditText.error = "Email cannot be empty"
            return false
        }
        if (password.isEmpty()) {
            passwordEditText.error = "Password cannot be empty"
            return false
        }
        return true
    }
}
