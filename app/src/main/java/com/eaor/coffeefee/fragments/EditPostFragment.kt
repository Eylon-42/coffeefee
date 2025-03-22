package com.eaor.coffeefee.fragments

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.eaor.coffeefee.BuildConfig
import com.eaor.coffeefee.MainActivity
import com.eaor.coffeefee.R
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso
import java.util.Arrays
import java.util.UUID
import java.io.ByteArrayOutputStream

class EditPostFragment : Fragment() {
    private lateinit var db: FirebaseFirestore
    private var imageUri: Uri? = null
    private var selectedLocation: LatLng? = null
    private var currentImageUrl: String? = null
    private var postId: String = ""
    private var selectedPlaceId: String? = null
    private var selectedPlaceName: String? = null
    private lateinit var imageView: ImageView
    private  var selectedPlaceRating: Float? = null
    private var selectedPlaceAddress: String? = null
    private var selectedPlacePhotoUrl: String? = null
    private var selectedPlaceDescription: String? = null

    private val REQUEST_CODE_IMAGE_PICK = 1

    private val startAutocomplete =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    val place = Autocomplete.getPlaceFromIntent(intent)
                    selectedLocation = place.latLng
                    selectedPlaceId = place.id
                    selectedPlaceName = place.name
                    selectedPlaceRating = place.rating?.toFloat()
                    selectedPlaceAddress = place.address
                    selectedPlaceDescription = place.editorialSummary?.toString()
                    view?.findViewById<TextView>(R.id.currentLocationText)?.text = place.name
                    
                    // Fetch or create coffee shop details when location is selected
                    fetchPlaceDetails(place.id)
                }
            }
        }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it
            imageView.visibility = View.VISIBLE
            Picasso.get()
                .load(uri)
                .into(imageView)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_post, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide bottom navigation when this fragment is displayed
        val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
        bottomNav?.visibility = View.GONE

        db = FirebaseFirestore.getInstance()
        
        setupToolbar(view)
        loadPostData(view)
        setupButtons(view)
    }

    override fun onDestroyView() {
        // Restore bottom navigation visibility when leaving this fragment
        val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
        bottomNav?.visibility = View.VISIBLE
        
        super.onDestroyView()
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Edit Post"

        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun loadPostData(view: View) {
        // Get postId from arguments
        postId = arguments?.getString("postId") ?: ""
        
        if (postId.isEmpty()) {
            Toast.makeText(context, "Error: Post ID not found", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        // First try to load from arguments (direct data)
        val experienceText = arguments?.getString("postText")
        val locationName = arguments?.getString("name")
        val latitude = arguments?.getDouble("latitude")
        val longitude = arguments?.getDouble("longitude")
        val placeId = arguments?.getString("placeId")
        currentImageUrl = arguments?.getString("imageUrl")

        if (experienceText != null) {
            // Data was passed directly, use it
            view.findViewById<EditText>(R.id.postReviewText).setText(experienceText)
            
            if (locationName != null && latitude != null && longitude != null) {
                selectedPlaceName = locationName
                selectedLocation = LatLng(latitude, longitude)
                selectedPlaceId = placeId
                view.findViewById<TextView>(R.id.currentLocationText).text = locationName
            }

            // Load image if URL exists
            currentImageUrl?.let { url ->
                Picasso.get()
                    .load(url)
                    .into(view.findViewById<ImageView>(R.id.imageView))
            }
        } else {
            // No direct data, fetch from Firestore
            db.collection("Posts").document(postId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // Load experience text
                        val text = document.getString("experienceDescription") ?: ""
                        view.findViewById<EditText>(R.id.postReviewText).setText(text)

                        // Load location
                        val locationMap = document.get("location") as? Map<*, *>
                        if (locationMap != null) {
                            val name = locationMap["name"] as? String ?: ""
                            val lat = (locationMap["latitude"] as? Double) ?: 0.0
                            val lng = (locationMap["longitude"] as? Double) ?: 0.0
                            val pid = locationMap["placeId"] as? String

                            selectedPlaceId = pid
                            selectedPlaceName = name
                            selectedLocation = LatLng(lat, lng)
                            view.findViewById<TextView>(R.id.currentLocationText).text = name
                        }

                        // Load image
                        currentImageUrl = document.getString("photoUrl")
                        currentImageUrl?.let { url ->
                            Picasso.get()
                                .load(url)
                                .into(view.findViewById<ImageView>(R.id.imageView))
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error loading post: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupButtons(view: View) {
        imageView = view.findViewById(R.id.imageView)
        
        // Hide imageView if no image
        if (currentImageUrl == null) {
            imageView.visibility = View.GONE
        }

        // Location button
        view.findViewById<Button>(R.id.changeLocationButton).setOnClickListener {
            launchPlacePicker()
        }

        // Image button
        view.findViewById<Button>(R.id.addImageButton).setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Save button
        view.findViewById<Button>(R.id.savePostButton).setOnClickListener {
            saveUpdatedPost()
        }
    }

    private fun launchPlacePicker() {
        try {
            val fields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.PHOTO_METADATAS,
                Place.Field.EDITORIAL_SUMMARY
            )
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                .build(requireActivity())
            startAutocomplete.launch(intent)
        } catch (e: Exception) {
            Log.e("EditPostFragment", "Error launching place picker: ${e.message}")
            Toast.makeText(context, "Error launching place picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveUpdatedPost() {
        val updatedText = view?.findViewById<EditText>(R.id.postReviewText)?.text.toString()
        
        if (updatedText.isEmpty()) {
            Toast.makeText(context, "Please enter a description", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading dialog
        val progressDialog = android.app.ProgressDialog(context).apply {
            setMessage("Updating post...")
            setCancelable(false)
            show()
        }

        val updateData = hashMapOf<String, Any>(
            "experienceDescription" to updatedText
        )

        // Update location if changed
        if (selectedLocation != null && selectedPlaceName != null) {
            updateData["location"] = hashMapOf(
                "name" to selectedPlaceName,
                "latitude" to selectedLocation!!.latitude,
                "longitude" to selectedLocation!!.longitude,
                "placeId" to (selectedPlaceId ?: "")
            )
        }

        // Handle image update
        if (imageUri != null) {
            uploadImageAndUpdatePost(updateData, progressDialog)
        } else {
            updatePostInFirestore(updateData, progressDialog)
        }
    }

    private fun uploadImageAndUpdatePost(updateData: HashMap<String, Any>, progressDialog: android.app.ProgressDialog) {
        val storageRef = FirebaseStorage.getInstance().reference.child("post_images/$postId.jpg")

        storageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    updateData["photoUrl"] = uri.toString()
                    updatePostInFirestore(updateData, progressDialog)
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(context, "Error uploading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updatePostInFirestore(updateData: HashMap<String, Any>, progressDialog: android.app.ProgressDialog) {
        db.collection("Posts").document(postId)
            .update(updateData)
            .addOnSuccessListener {
                Log.d("EditPostFragment", "Post updated successfully")
                
                // Update coffee shop data if location changed
                if (selectedLocation != null && selectedPlaceName != null && selectedPlaceId != null) {
                    updateCoffeeShopData(progressDialog)
                } else {
                    // If no coffee shop update needed, send broadcast immediately
                    sendPostUpdatedBroadcast()
                    progressDialog.dismiss()
                    Toast.makeText(context, "Post updated successfully", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(context, "Error updating post: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun updateCoffeeShopData(progressDialog: android.app.ProgressDialog) {
        if (selectedPlaceId == null) {
            sendPostUpdatedBroadcast()
            progressDialog.dismiss()
            Toast.makeText(context, "Post updated successfully", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }
        
        val coffeeShopData = hashMapOf<String, Any>(
            "name" to (selectedPlaceName ?: "Unnamed Coffee Shop"),
            "latitude" to (selectedLocation?.latitude ?: 0.0),
            "longitude" to (selectedLocation?.longitude ?: 0.0),
            "placeId" to selectedPlaceId!!,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        
        // Add optional fields if available
        if (selectedPlaceRating != null) {
            coffeeShopData["rating"] = selectedPlaceRating!!
        }
        
        // Handle nullable strings with default values
        val description = if (selectedPlaceDescription != null) selectedPlaceDescription!! else "No available description"
        coffeeShopData["description"] = description
        
        val address = if (selectedPlaceAddress != null) selectedPlaceAddress!! else "Address not available"
        coffeeShopData["address"] = address
        
        if (selectedPlacePhotoUrl != null) {
            coffeeShopData["photoUrl"] = selectedPlacePhotoUrl!!
        }
        
        // Update the coffee shop document
        db.collection("CoffeeShops")
            .document(selectedPlaceId!!)
            .update(coffeeShopData)
            .addOnSuccessListener {
                Log.d("EditPostFragment", "Coffee shop updated successfully")
                
                // Invalidate Picasso cache for coffee shop photo
                if (selectedPlacePhotoUrl != null) {
                    com.squareup.picasso.Picasso.get().invalidate(selectedPlacePhotoUrl)
                }
                
                sendPostUpdatedBroadcast()
                progressDialog.dismiss()
            }
            .addOnFailureListener { e ->
                Log.e("EditPostFragment", "Error updating coffee shop: ${e.message}")
                // Still send the broadcast even if coffee shop update fails
                sendPostUpdatedBroadcast()
                progressDialog.dismiss()
            }
    }
    
    private fun sendPostUpdatedBroadcast() {
        // Get the updated photo URL from Firestore to ensure we have the most recent one
        db.collection("Posts").document(postId)
            .get()
            .addOnSuccessListener { document ->
                // First check if the fragment is still attached to avoid crashes
                if (!isAdded) {
                    Log.d("EditPostFragment", "Fragment not attached, skipping broadcast")
                    // Set global refresh flags even if we can't send the broadcast
                    com.eaor.coffeefee.GlobalState.triggerRefreshAfterContentChange()
                    return@addOnSuccessListener
                }
                
                try {
                    val photoUrl = document.getString("photoUrl")
                    
                    // Broadcast that a post has been updated
                    val intent = Intent("com.eaor.coffeefee.POST_UPDATED")
                    intent.putExtra("postId", postId)
                    intent.putExtra("userId", FirebaseAuth.getInstance().currentUser?.uid ?: "")
                    
                    // Add photo URL for image cache invalidation
                    if (!photoUrl.isNullOrEmpty()) {
                        intent.putExtra("photoUrl", photoUrl)
                        
                        // Invalidate Picasso cache for the post photo
                        com.squareup.picasso.Picasso.get().invalidate(photoUrl)
                    }
                    
                    // Use the appropriate API based on Android version
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        // For Android 13+, specify broadcast flags
                        requireContext().sendBroadcast(intent, null)
                    } else {
                        // For older Android versions
                        requireContext().sendBroadcast(intent)
                    }
                    
                    // Set global refresh flags using the helper method
                    com.eaor.coffeefee.GlobalState.triggerRefreshAfterContentChange()
                    
                    Log.d("EditPostFragment", "Broadcast POST_UPDATED event with postId: $postId, photoUrl: $photoUrl")
                } catch (e: Exception) {
                    Log.e("EditPostFragment", "Error sending broadcast: ${e.message}")
                    // Ensure flags are set even if broadcast fails
                    com.eaor.coffeefee.GlobalState.triggerRefreshAfterContentChange()
                }
            }
            .addOnFailureListener { e ->
                // Check if fragment is still attached
                if (!isAdded) {
                    Log.d("EditPostFragment", "Fragment not attached during error, skipping broadcast")
                    // Set global refresh flags even if we can't send the broadcast
                    com.eaor.coffeefee.GlobalState.triggerRefreshAfterContentChange()
                    return@addOnFailureListener
                }
                
                try {
                    // If we can't get the photo URL, still send the broadcast
                    val intent = Intent("com.eaor.coffeefee.POST_UPDATED")
                    intent.putExtra("postId", postId)
                    intent.putExtra("userId", FirebaseAuth.getInstance().currentUser?.uid ?: "")
                    
                    // Use the appropriate API based on Android version
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        requireContext().sendBroadcast(intent, null)
                    } else {
                        requireContext().sendBroadcast(intent)
                    }
                    
                    // Set global refresh flags
                    com.eaor.coffeefee.GlobalState.triggerRefreshAfterContentChange()
                    
                    Log.d("EditPostFragment", "Broadcast POST_UPDATED event with postId: $postId (without photo URL)")
                } catch (e2: Exception) {
                    Log.e("EditPostFragment", "Error sending broadcast: ${e2.message}")
                    // Ensure flags are set even if broadcast fails
                    com.eaor.coffeefee.GlobalState.triggerRefreshAfterContentChange()
                }
                
                Log.e("EditPostFragment", "Error getting post photo URL: ${e.message}")
            }
    }

    private fun fetchPlaceDetails(placeId: String) {
        db.collection("CoffeeShops")
            .document(placeId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    selectedPlaceRating = document.getDouble("rating")?.toFloat()
                    selectedPlacePhotoUrl = document.getString("photoUrl")
                    selectedPlaceDescription = document.getString("description")
                    selectedPlaceAddress = document.getString("address")
                    Log.d("EditPostFragment", "Using existing coffee shop data")
                } else {
                    // Create new coffee shop data
                    createNewCoffeeShop()
                }
            }
            .addOnFailureListener { e ->
                Log.e("EditPostFragment", "Error checking for existing coffee shop", e)
                createNewCoffeeShop()
            }
    }

    private fun createNewCoffeeShop() {
        if (selectedPlaceId != null && selectedPlaceName != null && selectedLocation != null) {
            // First, try to get photo from Google Places API
            fetchGooglePlacePhoto(selectedPlaceId!!) { photoUrl ->
                // Now create the coffee shop with all data and fallbacks
                val coffeeShopData = hashMapOf<String, Any>(
                    "name" to (selectedPlaceName ?: "Unnamed Coffee Shop"),
                    "latitude" to (selectedLocation?.latitude ?: 0.0),
                    "longitude" to (selectedLocation?.longitude ?: 0.0),
                    "placeId" to selectedPlaceId!!,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                
                // Add optional fields if available
                if (selectedPlaceRating != null) {
                    coffeeShopData["rating"] = selectedPlaceRating!!
                }
                
                // Handle nullable strings with default values
                val description = if (selectedPlaceDescription != null) selectedPlaceDescription!! else "No available description"
                coffeeShopData["description"] = description
                
                val address = if (selectedPlaceAddress != null) selectedPlaceAddress!! else "Address not available"
                coffeeShopData["address"] = address
                
                if (photoUrl != null) {
                    coffeeShopData["photoUrl"] = photoUrl
                }
                
                // Save to Firestore
                db.collection("CoffeeShops")
                    .document(selectedPlaceId!!)
                    .set(coffeeShopData)
                    .addOnSuccessListener {
                        Log.d("EditPostFragment", "CoffeeShop created/updated with full data")
                    }
                    .addOnFailureListener { e ->
                        Log.e("EditPostFragment", "Error saving coffee shop: ${e.message}")
                    }
            }
        }
    }

    private fun fetchGooglePlacePhoto(placeId: String, callback: (String?) -> Unit) {
        // Get Fields to request
        val fields = listOf(
            Place.Field.PHOTO_METADATAS
        )
        
        MainActivity.placesClient.fetchPlace(
            FetchPlaceRequest.newInstance(placeId, fields)
        ).addOnSuccessListener { response ->
            val photoMetadata = response.place.photoMetadatas?.firstOrNull()
            
            if (photoMetadata != null) {
                // Create a fetch photo request
                val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                    .setMaxWidth(800)
                    .setMaxHeight(800)
                    .build()
                
                // Fetch the photo
                MainActivity.placesClient.fetchPhoto(photoRequest).addOnSuccessListener { fetchPhotoResponse ->
                    val bitmap = fetchPhotoResponse.bitmap
                    
                    // Upload to Firebase Storage
                    uploadPhotoToFirebase(bitmap, placeId, callback)
                    
                }.addOnFailureListener { exception ->
                    Log.e("EditPostFragment", "Error fetching photo: ${exception.message}")
                    callback(null)
                }
            } else {
                Log.d("EditPostFragment", "No photo metadata available for this place")
                callback(null)
            }
        }.addOnFailureListener { e ->
            Log.e("EditPostFragment", "Error fetching place details: ${e.message}")
            callback(null)
        }
    }

    // Method updated to upload photos to Firebase Storage
    private fun uploadPhotoToFirebase(bitmap: Bitmap, placeId: String, callback: (String?) -> Unit) {
        // Convert bitmap to byte array
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val imageData = baos.toByteArray()
        
        // Reference to storage location - using CoffeeShops folder as requested
        val storageRef = FirebaseStorage.getInstance().reference
            .child("CoffeeShops")
            .child("$placeId.jpg")
        
        // Upload photo to Firebase Storage
        val uploadTask = storageRef.putBytes(imageData)
        uploadTask.addOnSuccessListener {
            // Get the download URL
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                val photoUrl = uri.toString()
                Log.d("EditPostFragment", "Uploaded coffee shop image to Firebase Storage: $photoUrl")
                
                // Return the Firebase Storage URL
                callback(photoUrl)
            }.addOnFailureListener { e ->
                Log.e("EditPostFragment", "Error getting download URL: ${e.message}")
                callback(null)
            }
        }.addOnFailureListener { e ->
            Log.e("EditPostFragment", "Error uploading image to Firebase Storage: ${e.message}")
            callback(null)
        }
    }
}
