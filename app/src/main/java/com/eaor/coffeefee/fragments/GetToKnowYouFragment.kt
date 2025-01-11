package com.eaor.coffeefee.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.eaor.coffeefee.R

class GetToKnowYouFragment : Fragment() {
    private lateinit var answer1EditText: EditText
    private lateinit var answer2EditText: EditText
    private lateinit var answer3EditText: EditText
    private lateinit var nextButton: Button
    private lateinit var backButton: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_get_to_know_you, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().navigateUp()
            }
        })

        nextButton.setOnClickListener {
            if (validateAnswers()) {
                // Navigate to login fragment
                findNavController().navigate(R.id.action_getToKnowYouFragment_to_signInFragment)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBackButtonForTheme()
    }

    private fun updateBackButtonForTheme() {
        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        backButton.imageTintList = resources.getColorStateList(
            if (isDarkTheme) android.R.color.white else android.R.color.black,
            requireContext().theme
        )
    }

    private fun validateAnswers(): Boolean {
        var isValid = true

        if (answer1EditText.text.toString().isEmpty()) {
            answer1EditText.error = "Please answer this question"
            isValid = false
        }
        if (answer2EditText.text.toString().isEmpty()) {
            answer2EditText.error = "Please answer this question"
            isValid = false
        }
        if (answer3EditText.text.toString().isEmpty()) {
            answer3EditText.error = "Please answer this question"
            isValid = false
        }

        return isValid
    }
} 