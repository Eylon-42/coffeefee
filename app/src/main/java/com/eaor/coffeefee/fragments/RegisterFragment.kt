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
import com.eaor.coffeefee.MainActivity
import com.eaor.coffeefee.R

class RegisterFragment : Fragment() {
    private lateinit var nameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var backToSignInButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        nameEditText = view.findViewById(R.id.etName)
        emailEditText = view.findViewById(R.id.etEmail)
        passwordEditText = view.findViewById(R.id.etPassword)
        confirmPasswordEditText = view.findViewById(R.id.etVerifyPassword)
        registerButton = view.findViewById(R.id.btnRegister)
        backToSignInButton = view.findViewById(R.id.tvSignInLink)

        // Set click listeners
        registerButton.setOnClickListener {
            val name = nameEditText.text.toString()
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (validateInput(name, email, password, confirmPassword)) {
                // Check credentials
                if (email == "test@example.com" && password == "password123") {
                    // Successfully logged in, navigate to MainActivity
                    val intent = Intent(activity, MainActivity::class.java)
                    startActivity(intent)
                    activity?.finish() // Close the RegisterActivity
                } else {
                    // Invalid credentials
                    Toast.makeText(context, "Invalid email or password", Toast.LENGTH_SHORT).show()
                }
            }
        }

        backToSignInButton.setOnClickListener {
            // Go back to SignInFragment
            parentFragmentManager.popBackStack()
        }
    }

    private fun validateInput(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        if (name.isEmpty()) {
            nameEditText.error = "Name cannot be empty"
            return false
        }
        if (email.isEmpty()) {
            emailEditText.error = "Email cannot be empty"
            return false
        }
        if (password.isEmpty()) {
            passwordEditText.error = "Password cannot be empty"
            return false
        }
        if (confirmPassword.isEmpty()) {
            confirmPasswordEditText.error = "Please confirm your password"
            return false
        }
        if (password != confirmPassword) {
            confirmPasswordEditText.error = "Passwords do not match"
            return false
        }
        return true
    }
}
