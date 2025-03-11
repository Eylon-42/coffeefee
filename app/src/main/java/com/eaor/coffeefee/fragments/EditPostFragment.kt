package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.FeedItem
import com.google.firebase.firestore.FirebaseFirestore

class EditPostFragment : Fragment() {

    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_post, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()

        // Get the data passed from the previous fragment (UserProfileFragment)
        val bundle = arguments
        val postId = bundle?.getString("postId") ?: ""
        val experienceText = bundle?.getString("experienceText") ?: ""
        val location = FeedItem.Location(
            bundle?.getString("name") ?: "",
            bundle?.getDouble("latitude") ?: 0.0,
            bundle?.getDouble("longitude") ?: 0.0
        )
        val imageUrl = bundle?.getString("imageUrl")

        // Initialize the UI components
        val experienceEditText = view.findViewById<EditText>(R.id.postReviewText)
        val locationTextView = view.findViewById<TextView>(R.id.currentLocationText)

        // Set the initial values based on the passed data
        experienceEditText.setText(experienceText)
        location?.let {
            locationTextView.text = "Location: ${it.name}"
        }


        // Update button click listener
        val updateButton = view.findViewById<Button>(R.id.savePostButton)
        updateButton.setOnClickListener {
            val updatedExperienceText = experienceEditText.text.toString()

            // Save the updated post in Firestore
            if (updatedExperienceText.isNotEmpty()) {
                db.collection("Posts")
                    .document(postId)
                    .update("experienceDescription", updatedExperienceText)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Post updated successfully", Toast.LENGTH_SHORT).show()
                        requireActivity().onBackPressed() // Navigate back
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Error updating post: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(context, "Please enter experience description", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
