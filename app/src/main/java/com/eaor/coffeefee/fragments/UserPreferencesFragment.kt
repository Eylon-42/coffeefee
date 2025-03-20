package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.eaor.coffeefee.R
import com.eaor.coffeefee.utils.VertexAIService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserPreferencesFragment : Fragment() {
    private lateinit var favoriteCoffeeDrinkInput: EditText
    private lateinit var dietaryNeedsInput: EditText
    private lateinit var preferredAtmosphereInput: EditText
    private lateinit var locationPreferenceInput: EditText
    private lateinit var submitButton: Button
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val vertexAIService = VertexAIService.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_preferences, container, false)
        
        // Setup toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Your Preferences"
        
        // Initialize UI elements
        favoriteCoffeeDrinkInput = view.findViewById(R.id.favoriteCoffeeDrinkInput)
        dietaryNeedsInput = view.findViewById(R.id.dietaryNeedsInput)
        preferredAtmosphereInput = view.findViewById(R.id.preferredAtmosphereInput)
        locationPreferenceInput = view.findViewById(R.id.locationPreferenceInput)
        submitButton = view.findViewById(R.id.submitButton)
        
        setupSubmitButton()
        
        return view
    }
    
    private fun setupSubmitButton() {
        submitButton.setOnClickListener {
            val favoriteDrink = favoriteCoffeeDrinkInput.text.toString().trim()
            val dietaryNeeds = dietaryNeedsInput.text.toString().trim()
            val atmosphere = preferredAtmosphereInput.text.toString().trim()
            val location = locationPreferenceInput.text.toString().trim()
            
            if (favoriteDrink.isEmpty() || atmosphere.isEmpty() || location.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            submitButton.isEnabled = false
            submitButton.text = "Processing..."
            
            generateUserTags(favoriteDrink, dietaryNeeds, atmosphere, location)
        }
    }
    
    private fun generateUserTags(
        favoriteDrink: String,
        dietaryNeeds: String,
        atmosphere: String,
        location: String
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Build the prompt for VertexAI
                val prompt = vertexAIService.buildTagGenerationPrompt(
                    favoriteDrink, 
                    dietaryNeeds, 
                    atmosphere, 
                    location
                )
                
                // Call VertexAI to generate tags
                val result = vertexAIService.generateCoffeeExperience(prompt, location, emptyList())
                
                result.fold(
                    onSuccess = { response ->
                        val tags = parseTagsFromResponse(response)
                        Log.d("UserPreferencesFragment", "Generated tags: $tags")
                        
                        // Save tags to Firestore
                        saveUserTagsToFirestore(tags)
                    },
                    onFailure = { error ->
                        Log.e("UserPreferencesFragment", "Failed to generate tags", error)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to process your preferences. Please try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                            submitButton.isEnabled = true
                            submitButton.text = "Submit"
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("UserPreferencesFragment", "Error generating user tags", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "An error occurred. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                    submitButton.isEnabled = true
                    submitButton.text = "Submit"
                }
            }
        }
    }
    
    private fun parseTagsFromResponse(response: String): List<String> {
        // Parse the comma-separated response into a list of tags
        return response
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
    }
    
    private fun saveUserTagsToFirestore(tags: List<String>) {
        val userId = auth.currentUser?.uid
        
        if (userId == null) {
            Log.e("UserPreferencesFragment", "User not logged in")
            return
        }
        
        val userData = mapOf(
            "tags" to tags,
            "preferencesCompleted" to true,
            "lastUpdated" to System.currentTimeMillis()
        )
        
        db.collection("users")
            .document(userId)
            .update(userData)
            .addOnSuccessListener {
                Log.d("UserPreferencesFragment", "User tags saved successfully")
                
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Preferences saved successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Navigate to suggestions screen
                    findNavController().navigate(R.id.action_userPreferencesFragment_to_suggestionFragment)
                }
            }
            .addOnFailureListener { e ->
                Log.e("UserPreferencesFragment", "Error saving user tags", e)
                
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to save preferences. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                    submitButton.isEnabled = true
                    submitButton.text = "Submit"
                }
            }
    }
} 