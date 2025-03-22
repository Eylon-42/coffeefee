package com.eaor.coffeefee.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
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

class GetToKnowYouFragment : Fragment() {
    private lateinit var coffeeDrinkEditText: EditText
    private lateinit var dietaryNeedsEditText: EditText
    private lateinit var atmosphereEditText: EditText
    private lateinit var locationPreferenceEditText: EditText
    private lateinit var nextButton: View
    private lateinit var backButton: View
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val vertexAIService = VertexAIService.getInstance()

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
        coffeeDrinkEditText = view.findViewById(R.id.etCoffeeDrink)
        dietaryNeedsEditText = view.findViewById(R.id.etDietaryNeeds)
        atmosphereEditText = view.findViewById(R.id.etAtmosphere)
        locationPreferenceEditText = view.findViewById(R.id.etLocationPreference)
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

        // Handle next button click
        nextButton.setOnClickListener {
            if (validateAnswers()) {
                saveUserDetails()
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

    private fun saveUserDetails() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // Prepare data in the required format
        val userDetails = hashMapOf(
            "favoriteCoffeeDrink" to coffeeDrinkEditText.text.toString(),
            "dietaryNeeds" to dietaryNeedsEditText.text.toString(),
            "preferredAtmosphere" to atmosphereEditText.text.toString(),
            "locationPreference" to locationPreferenceEditText.text.toString()
        )

        // Save to Firebase
        db.collection("Users")
            .document(userId)
            .update("preferences", userDetails)
            .addOnSuccessListener {
                // After saving user details, generate AI tags based on user preferences
                generateAITags(userDetails) { generatedTags ->
                    if (generatedTags.isNotEmpty()) {
                        // Save the generated tags to the user's document
                        db.collection("Users")
                            .document(userId)
                            .update("tags", generatedTags)
                            .addOnSuccessListener {
                                // Navigate to sign-in fragment after saving the tags
                                findNavController().navigate(R.id.action_getToKnowYouFragment_to_signInFragment)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    context,
                                    "Failed to save tags: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    } else {
                        Toast.makeText(context, "Failed to generate tags", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    "Failed to save user details: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun generateAITags(userDetails: Map<String, String>, callback: (List<String>) -> Unit) {
        lifecycleScope.launch {
            try {
                val favoriteDrink = userDetails["favoriteCoffeeDrink"] ?: ""
                val dietaryNeeds = userDetails["dietaryNeeds"] ?: ""
                val atmosphere = userDetails["preferredAtmosphere"] ?: ""
                val location = userDetails["locationPreference"] ?: ""
                
                // Use VertexAIService to generate tags from preferences
                val result = vertexAIService.generateTagsFromPreferences(
                    favoriteDrink = favoriteDrink,
                    dietaryNeeds = dietaryNeeds,
                    atmosphere = atmosphere,
                    location = location
                )
                
                result.fold(
                    onSuccess = { tags ->
                        Log.d("GetToKnowYouFragment", "Successfully generated tags: $tags")
                        withContext(Dispatchers.Main) {
                            callback(tags)
                        }
                    },
                    onFailure = { error ->
                        Log.e("GetToKnowYouFragment", "Failed to generate tags", error)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Could not generate preferences: ${error.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            callback(emptyList())
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("GetToKnowYouFragment", "Error in generateAITags", e)
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }

    private fun validateAnswers(): Boolean {
        // Validate preferences
        if (!isValidAnswer(coffeeDrinkEditText.text.toString())) {
            coffeeDrinkEditText.error = "Invalid input: letters only, max 15 characters"
            return false
        }
        if (!isValidAnswer(dietaryNeedsEditText.text.toString())) {
            dietaryNeedsEditText.error = "Invalid input: letters only, max 15 characters"
            return false
        }
        if (!isValidAnswer(atmosphereEditText.text.toString())) {
            atmosphereEditText.error = "Invalid input: letters only, max 15 characters"
            return false
        }
        if (!isValidAnswer(locationPreferenceEditText.text.toString())) {
            locationPreferenceEditText.error = "Invalid input: letters only, max 15 characters"
            return false
        }
        return true
    }

    private fun isValidAnswer(input: String): Boolean {
        return input.isNotEmpty()
    }
    
    companion object {
        private const val TAG = "GetToKnowYouFragment"
    }
}