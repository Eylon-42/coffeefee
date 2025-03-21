package com.eaor.coffeefee.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.eaor.coffeefee.MainActivity
import com.eaor.coffeefee.R
import com.eaor.coffeefee.viewmodels.AuthViewModel

class SignInFragment : Fragment() {
    private lateinit var viewModel: AuthViewModel
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var signInButton: Button
    private lateinit var registerTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_log_in, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(requireActivity())[AuthViewModel::class.java]
        
        // Initialize views
        emailEditText = view.findViewById(R.id.etEmail)
        passwordEditText = view.findViewById(R.id.etPassword)
        signInButton = view.findViewById(R.id.btnSignIn)
        registerTextView = view.findViewById(R.id.tvRegister)
        
        signInButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            
            if (email.isNotEmpty() && password.isNotEmpty()) {
                signIn(email, password)
            } else {
                Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        registerTextView.setOnClickListener {
            findNavController().navigate(R.id.action_signInFragment_to_registerFragment)
        }
        
        // Set up observers
        setupObservers()
    }
    
    private fun setupObservers() {
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            signInButton.isEnabled = !isLoading
            // You could also show a progress indicator here
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }
        
        // Observe current user
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                // Navigate to MainActivity
                startActivity(Intent(requireActivity(), MainActivity::class.java))
                requireActivity().finish()
            }
        }
    }

    private fun signIn(email: String, password: String) {
        viewModel.signIn(email, password)
    }
}
