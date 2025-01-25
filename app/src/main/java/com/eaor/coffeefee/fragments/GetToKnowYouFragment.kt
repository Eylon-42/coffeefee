package com.eaor.coffeefee.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.eaor.coffeefee.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.content.ContextCompat

class GetToKnowYouFragment : Fragment() {
    private lateinit var answer1EditText: EditText
    private lateinit var answer2EditText: EditText
    private lateinit var answer3EditText: EditText
    private lateinit var nextButton: View
    private lateinit var backButton: View
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_get_to_know_you, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        answer1EditText = view.findViewById(R.id.etAnswer1)
        answer2EditText = view.findViewById(R.id.etAnswer2)
        answer3EditText = view.findViewById(R.id.etAnswer3)
        nextButton = view.findViewById(R.id.btnNext)
        backButton = view.findViewById(R.id.btnBack)

        // Set back button color based on current theme
        updateBackButtonForTheme()

        // Handle back button click
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Handle system back button
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().navigateUp()
                }
            }
        )

        nextButton.setOnClickListener {
            if (validateAnswers()) {
                savePreferences()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBackButtonForTheme()
    }

    private fun updateBackButtonForTheme() {
        val backBtn = view?.findViewById<ImageButton>(R.id.btnBack)
        backBtn?.setColorFilter(
            ContextCompat.getColor(requireContext(), android.R.color.black)
        )
    }

    private fun savePreferences() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val preferences = hashMapOf(
            "first" to answer1EditText.text.toString(),
            "second" to answer2EditText.text.toString(),
            "third" to answer3EditText.text.toString()
        )

        db.collection("Users")
            .document(userId)
            .update("preferences", preferences)
            .addOnSuccessListener {
                // Navigate to sign in fragment after successful save
                findNavController().navigate(R.id.action_getToKnowYouFragment_to_signInFragment)
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    "Failed to save preferences: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun validateAnswers(): Boolean {
        if (answer1EditText.text.isNullOrEmpty()) {
            answer1EditText.error = "Please answer this question"
            return false
        }
        if (answer2EditText.text.isNullOrEmpty()) {
            answer2EditText.error = "Please answer this question"
            return false
        }
        if (answer3EditText.text.isNullOrEmpty()) {
            answer3EditText.error = "Please answer this question"
            return false
        }
        return true
    }
} 