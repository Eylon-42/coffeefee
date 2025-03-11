package com.eaor.coffeefee.fragments

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.FeedItem
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso

class EditPostFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private var imageUri: Uri? = null // To hold the image URI
    private var selectedLocation: LatLng? = null

    private val REQUEST_CODE_IMAGE_PICK = 1 // Code for picking an image

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_post, container, false)
    }

    private val startAutocomplete =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    val place = Autocomplete.getPlaceFromIntent(intent)
                    selectedLocation = place.latLng
                    view?.findViewById<TextView>(R.id.currentLocationText)?.text =
                        place.name?.toString()
                }
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                Log.d("AddPostFragment", "User canceled autocomplete")
            }
        }

    private fun launchPlacePicker() {
        // Check if fragment is attached
        if (isAdded) {
            try {
                val fields = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.ADDRESS
                )
                val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                    .build(requireActivity())
                startAutocomplete.launch(intent)
            } catch (e: Exception) {
                Log.e("AddPostFragment", "Error launching place picker: ${e.message}")
                Toast.makeText(requireContext(), "Error launching place picker", Toast.LENGTH_SHORT).show()
            }
        }
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
        val imageView = view.findViewById<ImageView>(R.id.imageView)

        // Set the initial values based on the passed data
        experienceEditText.setText(experienceText)
        location?.let {
            locationTextView.text = "Location: ${it.name}"
        }

        if (imageUrl != null) {
            Picasso.get().load(imageUrl).into(imageView)
        }

        // Button for changing location
        val changeLocationButton = view.findViewById<Button>(R.id.changeLocationButton)
        changeLocationButton.setOnClickListener {
            // Open a location picker activity or allow manual entry
            // For simplicity, we're just using a placeholder function here.
            launchPlacePicker()
        }

        // Button for changing photo
        val changePhotoButton = view.findViewById<Button>(R.id.addImageButton)
        changePhotoButton.setOnClickListener {
            // Open an image picker to select a new photo
            openImagePicker()
        }

        // Update button click listener
        val updateButton = view.findViewById<Button>(R.id.savePostButton)
        updateButton.setOnClickListener {
            val updatedExperienceText = experienceEditText.text.toString()

            // Save the updated post in Firestore
            if (updatedExperienceText.isNotEmpty()) {
                val updateData = hashMapOf<String, Any>()
                updateData["experienceDescription"] = updatedExperienceText
                if (selectedLocation != null) {
                    updateData["location"] = hashMapOf(
                        "name" to locationTextView.text.toString(),
                        "latitude" to selectedLocation?.latitude,
                        "longitude" to selectedLocation?.longitude
                    )
                } else {
                    updateData["location"] = hashMapOf(
                        "name" to location.name,
                        "latitude" to location.latitude,
                        "longitude" to location.longitude
                    )
                }

                // Update image URL if changed
                imageUri?.let { uri ->
                    uploadImageToFirebase(uri, postId) { imageUrl ->
                        if (imageUrl != null) {
                            updateData["photoUrl"] = imageUrl
                        }

                        // Now update Firestore
                        db.collection("Posts")
                            .document(postId)
                            .update(updateData)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Post updated successfully", Toast.LENGTH_SHORT).show()
                                requireActivity().onBackPressed() // Navigate back
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error updating post: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } ?: run {
                    // If no new image, just update the post without image
                    db.collection("Posts")
                        .document(postId)
                        .update(updateData)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Post updated successfully", Toast.LENGTH_SHORT).show()
                            requireActivity().onBackPressed() // Navigate back
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Error updating post: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                Toast.makeText(context, "Please enter experience description", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openImagePicker() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        } else {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_CODE_IMAGE_PICK)
        }
    }

    private fun uploadImageToFirebase(imageUri: Uri, postId: String, callback: (String?) -> Unit) {
        val storageRef = FirebaseStorage.getInstance().reference.child("post_images/$postId.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    callback(uri.toString()) // Pass the download URL to the callback
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error uploading image: ${e.message}", Toast.LENGTH_SHORT).show()
                callback(null)
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            imageUri = data?.data
            val imageView = view?.findViewById<ImageView>(R.id.imageView)
            imageView?.let {
                Picasso.get().load(imageUri).into(it)
            }
        }
    }
}
