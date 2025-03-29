package com.eaor.coffeefee.fragments

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.MainActivity
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.ImageAdapter
import com.eaor.coffeefee.utils.VertexAIService
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class AddPostFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var bottomNav: View
    private var selectedLocation: LatLng? = null
    private val selectedImages = mutableListOf<Uri>()
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var placesClient: PlacesClient
    private var selectedPlaceName: String? = null
    private var selectedPlaceId: String? = null
    private var selectedPlaceRating: Float? = null
    private var selectedPlacePhotoUrl: String? = null
    private var selectedPlaceDescription: String? = null
    private var selectedPlaceAddress: String? = null
    private val vertexAIService = VertexAIService.getInstance()

    // Image picker result launcher
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImages.clear()
            selectedImages.add(it)
            updateImagesRecyclerView()
        }
    }

    private fun uploadImageToStorage(uri: Uri, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val storageReference = FirebaseStorage.getInstance().reference
        val photoRef = storageReference.child("Posts/${UUID.randomUUID()}.jpg") // Using UUID to ensure unique filename

        val uploadTask = photoRef.putFile(uri)
        uploadTask.addOnSuccessListener {
            // Get the download URL once the upload is successful
            photoRef.downloadUrl.addOnSuccessListener { uri ->
                onSuccess(uri.toString()) // Pass the download URL to the success callback
            }.addOnFailureListener { exception ->
                onFailure(exception) // Handle any errors during URL retrieval
            }
        }.addOnFailureListener { exception ->
            onFailure(exception) // Handle errors during upload
        }
    }

    private val startAutocomplete =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    val place = Autocomplete.getPlaceFromIntent(intent)
                    Log.d("AddPostFragment", "Place: ${place.name}, ${place.id}")
                    selectedLocation = place.latLng
                    selectedPlaceName = place.name
                    selectedPlaceId = place.id
                    view?.findViewById<TextView>(R.id.selectedLocationText)?.text = 
                        place.name.toString()
                    
                    // Fetch place details only once
                    fetchPlaceDetails(place.id)
                    
                    // Offer to generate a description based on the selected place
                    showGenerateDescriptionOption()
                }
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                Log.d("AddPostFragment", "User canceled autocomplete")
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_add_post, container, false)

        // Initialize Firebase only once
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        // Initialize Places Client
        placesClient = Places.createClient(requireContext())

        // Handle Bottom Navigation visibility
        bottomNav = requireActivity().findViewById(R.id.bottom_nav)
        bottomNav.visibility = View.GONE

        setupToolbar(view)
        setupLocationButton(view)
        setupImagePicker(view)
        setupPostButton(view)

        val searchView = view.findViewById<SearchView>(R.id.searchView)
        if (searchView == null) {
            Log.e("AddPostFragment", "SearchView is null")
        } else {
            setupSearchView(searchView)
        }

        return view
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "New Post"

        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupLocationButton(view: View) {
        view.findViewById<Button>(R.id.selectLocationButton).setOnClickListener {
            launchPlacePicker()
        }
    }

    private fun setupImagePicker(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.imagesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        imageAdapter = ImageAdapter(selectedImages) { position: Int ->
            selectedImages.removeAt(position) // Remove the image at the clicked position
            updateImagesRecyclerView()
        }
        recyclerView.adapter = imageAdapter
        recyclerView.visibility = View.GONE

        view.findViewById<Button>(R.id.addImageButton).setOnClickListener {
            // Allow the user to pick only one image
            getContent.launch("image/*")
        }
    }

    private fun updateImagesRecyclerView() {
        imageAdapter.notifyDataSetChanged()
        view?.findViewById<RecyclerView>(R.id.imagesRecyclerView)?.let { recyclerView ->
            recyclerView.visibility = if (selectedImages.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }

    private fun setupPostButton(view: View) {
        view.findViewById<Button>(R.id.postButton).setOnClickListener {
            // Check if fragment is attached before interacting with context
            if (isAdded) {
                val experienceDescription = view.findViewById<EditText>(R.id.experienceDescription).text.toString()
                val currentUser = auth.currentUser
                val userId = currentUser?.uid

                if (experienceDescription.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter a description", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (selectedLocation == null) {
                    Toast.makeText(requireContext(), "Please select a location", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Show loading dialog
                val progressDialog = android.app.ProgressDialog(context).apply {
                    setMessage("Creating post...")
                    setCancelable(false)
                    show()
                }

                // Generate tags for the post using VertexAI
                generateTags(experienceDescription) { tags ->
                    // If an image is selected, upload it
                    if (selectedImages.isNotEmpty()) {
                        val imageUri = selectedImages[0] // Only one image can be in the list
                        uploadImageToStorage(imageUri,
                            onSuccess = { imageUrl ->
                                createPostInFirestore(experienceDescription, userId, imageUrl, tags, progressDialog)
                            },
                            onFailure = { exception ->
                                progressDialog.dismiss()
                                Log.e("AddPostFragment", "Image upload failed: ${exception.message}")
                                Toast.makeText(requireContext(), "Image upload failed", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        // If no image is selected, just post without a photo
                        createPostInFirestore(experienceDescription, userId, null, tags, progressDialog)
                    }
                }
            }
        }
    }
    
    private fun showGenerateDescriptionOption() {
        val generateDescBtn = view?.findViewById<ImageView>(R.id.generateDescriptionButton)
        if (generateDescBtn != null) {
            generateDescBtn.visibility = View.VISIBLE
            generateDescBtn.setOnClickListener {
                if (selectedPlaceName != null) {
                    val descriptionBox = view?.findViewById<EditText>(R.id.experienceDescription)
                    if (descriptionBox != null) {
                        val currentText = descriptionBox.text.toString()
                        generateDescription(currentText)
                    }
                }
            }
        }
    }
    
    private fun generateDescription(userInput: String) {
        val locationName = selectedPlaceName ?: "Unknown location"
        
        lifecycleScope.launch {
            try {
                // If user has provided some input, use it as a base for enhancement
                val prompt = if (userInput.isNotEmpty()) {
                    userInput
                } else {
                    "I'm at a coffee shop"
                }
                
                // Get tags (coffee-related and ambiance terms)
                val tags = listOf("coffee", "cafe", locationName.split(" ")[0].lowercase())
                
                // Use VertexAI service to generate the description
                val result = vertexAIService.generateCoffeeExperience(prompt, locationName, tags)
                
                result.fold(
                    onSuccess = { generatedDescription ->
                        // Update the description box
                        view?.findViewById<EditText>(R.id.experienceDescription)?.setText(generatedDescription)
                    },
                    onFailure = { error ->
                        Log.e("AddPostFragment", "Failed to generate description", error)
                        Toast.makeText(
                            requireContext(),
                            "Couldn't generate description: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            } catch (e: Exception) {
                Log.e("AddPostFragment", "Error generating description", e)
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun generateTags(description: String, callback: (List<String>) -> Unit) {
        lifecycleScope.launch {
            try {
                val result = vertexAIService.generateTags(description)
                
                result.fold(
                    onSuccess = { tags ->
                        withContext(Dispatchers.Main) {
                            callback(tags)
                        }
                    },
                    onFailure = { error ->
                        Log.e("AddPostFragment", "Failed to generate tags", error)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Could not generate tags: ${error.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            callback(emptyList())
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("AddPostFragment", "Error generating tags", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    callback(emptyList())
                }
            }
        }
    }

    private fun createPostInFirestore(
        experienceDescription: String,
        userId: String?,
        imageUrl: String?,
        tags: List<String>,
        progressDialog: android.app.ProgressDialog
    ) {
        if (selectedLocation == null || selectedPlaceName == null) {
            Toast.makeText(requireContext(), "Please select a coffee shop location", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Create the post data
        val postData = mapOf(
            "UserId" to userId,
            "experienceDescription" to experienceDescription,
            "location" to mapOf(
                "name" to view?.findViewById<TextView>(R.id.selectedLocationText)?.text.toString(),
                "latitude" to selectedLocation?.latitude,
                "longitude" to selectedLocation?.longitude,
                "placeId" to selectedPlaceId
            ),
            "timestamp" to System.currentTimeMillis(),
            "photoUrl" to imageUrl,
            "likeCount" to 0,
            "commentCount" to 0,
            "likes" to listOf<String>()
        )
        Log.d("AddPostFragment-Tags", "Post tags: $tags")
        
        // First add the post
        db.collection("Posts")
            .add(postData)
            .addOnSuccessListener { documentReference ->
                Log.d("AddPostFragment", "Post added with ID: ${documentReference.id}")
                
                // Send broadcast to notify other fragments of the new post
                val intent = Intent("com.eaor.coffeefee.POST_ADDED")
                intent.putExtra("postId", documentReference.id)
                intent.putExtra("userId", userId)
                
                // Set global state flags for refresh
                com.eaor.coffeefee.GlobalState.triggerRefreshAfterContentChange()
                
                // Send the broadcast
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requireContext().sendBroadcast(intent, null)
                } else {
                    requireContext().sendBroadcast(intent)
                }
                Log.d("AddPostFragment", "Broadcast sent for new post: ${documentReference.id}")

                // Now check if the coffee shop exists
                val coffeeShopRef = db.collection("CoffeeShops").document(selectedPlaceId!!)

                coffeeShopRef.get().addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Coffee shop exists, so merge the new tags with the existing tags
                        val existingTags = document.get("tags") as? List<String> ?: listOf()
                        val updatedTags = (existingTags + tags).distinct()  // Merge and remove duplicates

                        // Update the coffee shop's tags field
                        coffeeShopRef.update("tags", updatedTags)
                            .addOnSuccessListener {
                                Log.d("AddPostFragment", "Tags updated successfully")
                                Toast.makeText(
                                    requireContext(),
                                    "Post added and tags updated",
                                    Toast.LENGTH_SHORT
                                ).show()
                                findNavController().navigateUp()
                                progressDialog.dismiss()
                            }
                            .addOnFailureListener { e ->
                                Log.e("AddPostFragment", "Error updating tags", e)
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to update tags: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                progressDialog.dismiss()
                            }
                    } else {
                        // Coffee shop does not exist, so create a new document with tags
                        val coffeeShopData = mapOf(
                            "name" to selectedPlaceName,
                            "rating" to selectedPlaceRating,
                            "description" to (selectedPlaceDescription ?: "No available description"),
                            "latitude" to selectedLocation!!.latitude,
                            "longitude" to selectedLocation!!.longitude,
                            "placeId" to selectedPlaceId,
                            "photoUrl" to selectedPlacePhotoUrl,
                            "address" to (selectedPlaceAddress ?: ""),
                            "tags" to tags // Include tags here
                        )

                        // Create the new coffee shop document
                        coffeeShopRef.set(coffeeShopData)
                            .addOnSuccessListener {
                                Log.d("AddPostFragment", "Coffee shop added successfully")
                                Toast.makeText(
                                    requireContext(),
                                    "Post added and coffee shop data saved",
                                    Toast.LENGTH_SHORT
                                ).show()
                                findNavController().navigateUp()
                                progressDialog.dismiss()
                            }
                            .addOnFailureListener { e ->
                                Log.e("AddPostFragment", "Error adding coffee shop", e)
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to add coffee shop: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                progressDialog.dismiss()
                            }
                    }
                }
                    .addOnFailureListener { e ->
                        Log.e("AddPostFragment", "Error checking coffee shop existence", e)
                        Toast.makeText(
                            requireContext(),
                            "Error checking coffee shop: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        progressDialog.dismiss()
                    }
            }
            .addOnFailureListener { exception ->
                Log.e("AddPostFragment", "Error adding post: ${exception.message}")
                Toast.makeText(requireContext(), "Error adding post", Toast.LENGTH_SHORT).show()
                progressDialog.dismiss()
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
            Log.e("AddPostFragment", "Error launching place picker: ${e.message}")
            Toast.makeText(requireContext(), "Error launching place picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearchView(searchView: SearchView) {
        Log.d("AddPostFragment", "SearchView: $searchView")
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { fetchAutocompletePredictions(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Optionally handle text change
                return true
            }
        })
    }

    private fun fetchAutocompletePredictions(query: String) {
        if (!::placesClient.isInitialized) {
            Log.e("AddPostFragment", "PlacesClient is not initialized")
            return
        }

        val token = AutocompleteSessionToken.newInstance()
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(token)
            .setCountries("IL") // Restrict to Israel
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                for (prediction in response.autocompletePredictions) {
                    Log.i("AddPostFragment", "Place ID: ${prediction.placeId}, Name: ${prediction.getPrimaryText(null)}")
                    // Handle predictions (e.g., display in a RecyclerView)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("AddPostFragment", "Error fetching predictions: ${exception.message}")
            }
    }

    private fun fetchPlaceDetails(placeId: String) {
        // First check if the coffee shop already exists in Firestore
        db.collection("CoffeeShops")
            .document(placeId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Coffee shop already exists, use existing data
                    selectedPlaceRating = document.getDouble("rating")?.toFloat()
                    selectedPlacePhotoUrl = document.getString("photoUrl")
                    selectedPlaceDescription = document.getString("description")
                    selectedPlaceAddress = document.getString("address")
                    Log.d("AddPostFragment", "Using existing coffee shop data: ${document.data}")
                } else {
                    // Coffee shop doesn't exist, create it with complete data from Google
                    createNewCoffeeShop(placeId)
                }
            }
            .addOnFailureListener { e ->
                Log.e("AddPostFragment", "Error checking for existing coffee shop", e)
                // Fallback to Places API
                createNewCoffeeShop(placeId)
            }
    }

    private fun createNewCoffeeShop(placeId: String) {
        // Get Fields to request
        val fields = listOf(
            Place.Field.ID, 
            Place.Field.NAME, 
            Place.Field.LAT_LNG, 
            Place.Field.RATING, 
            Place.Field.ADDRESS,
            Place.Field.PHOTO_METADATAS,
            Place.Field.EDITORIAL_SUMMARY
        )
        
        Log.d("AddPostFragment", "Creating new coffee shop with place ID: $placeId")
        
        MainActivity.placesClient.fetchPlace(
            FetchPlaceRequest.newInstance(placeId, fields)
        ).addOnSuccessListener { response ->
            val place = response.place
            Log.d("AddPostFragment", "Successfully fetched place: ${place.name}")
            
            // Get Google photo URL
            val photoMetadata = place.photoMetadatas?.firstOrNull()
            if (photoMetadata != null) {
                // Create a FetchPhotoRequest
                val photoRequest = FetchPhotoRequest.builder(photoMetadata).build()
                MainActivity.placesClient.fetchPhoto(photoRequest).addOnSuccessListener { fetchPhotoResponse ->
                    // Get the bitmap from the photo response
                    val bitmap = fetchPhotoResponse.bitmap
                    Log.d("AddPostFragment", "Successfully fetched photo for place: ${place.name}")
                    
                    // Upload to Firebase Storage and get URL
                    uploadBitmapToFirebase(bitmap, placeId) { url ->
                        // Now save coffee shop with the photo URL
                        saveCoffeeShopWithPhotoUrl(place, placeId, url)
                    }
                }.addOnFailureListener { e ->
                    // Handle photo fetch failure, but still save the coffee shop
                    Log.e("AddPostFragment", "Error fetching place photo: ${e.message}")
                    saveCoffeeShopWithPhotoUrl(place, placeId, null)
                }
            } else {
                Log.d("AddPostFragment", "No photo available for place: ${place.name}")
                saveCoffeeShopWithPhotoUrl(place, placeId, null)
            }
        }.addOnFailureListener { e ->
            Log.e("AddPostFragment", "Error fetching place details: ${e.message}")
        }
    }

    private fun uploadBitmapToFirebase(bitmap: Bitmap, placeId: String, callback: (String?) -> Unit) {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference.child("CoffeeShops/$placeId.jpg")
        
        // Resize bitmap to a reasonable size before uploading
        val resizedBitmap = resizeBitmap(bitmap, 1200) // Resize to max 1200px on largest dimension
        
        val baos = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val data = baos.toByteArray()
        
        Log.d("AddPostFragment", "Uploading photo to Firebase for place ID: $placeId")
        
        storageRef.putBytes(data).continueWithTask { task ->
            if (!task.isSuccessful) {
                Log.e("AddPostFragment", "Error in upload task: ${task.exception?.message}")
                throw task.exception ?: Exception("Upload failed")
            }
            storageRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("AddPostFragment", "Successfully uploaded photo, URL: ${task.result}")
                callback(task.result.toString())
            } else {
                Log.e("AddPostFragment", "Error uploading photo: ${task.exception?.message}")
                callback(null)
            }
        }
    }

    // Helper function to resize bitmap proportionally
    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap // No need to resize
        }
        
        val ratio = width.toFloat() / height.toFloat()
        
        val newWidth: Int
        val newHeight: Int
        
        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun saveCoffeeShopWithPhotoUrl(place: Place, placeId: String, photoUrl: String?) {
        // Create coffee shop data with all available information and fallbacks
        val coffeeShopData = hashMapOf(
            "name" to (place.name ?: "Unnamed Coffee Shop"),
            "rating" to place.rating?.toDouble(),
            "description" to (place.editorialSummary?.toString() 
                ?: "No available description"),
            "latitude" to (place.latLng?.latitude ?: 0.0),
            "longitude" to (place.latLng?.longitude ?: 0.0),
            "placeId" to placeId,
            "address" to (place.address ?: "Address not available"),
            "photoUrl" to photoUrl,
            "createdAt" to FieldValue.serverTimestamp()
        )
        
        Log.d("AddPostFragment", "Saving coffee shop with data: $coffeeShopData")
        
        // Save to Firestore
        db.collection("CoffeeShops")
            .document(placeId)
            .set(coffeeShopData)
            .addOnSuccessListener {
                Log.d("AddPostFragment", "CoffeeShop saved successfully with photo URL: $photoUrl")
                
                // Update local variables for use in post creation
                selectedPlaceRating = place.rating?.toFloat()
                selectedPlacePhotoUrl = photoUrl
                selectedPlaceDescription = place.editorialSummary?.toString() 
                    ?: "No available description"
                selectedPlaceAddress = place.address ?: "Address not available"
            }
            .addOnFailureListener { e ->
                Log.e("AddPostFragment", "Error saving coffee shop: ${e.message}")
            }
    }

    companion object {
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 1
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Restore the visibility of the bottom navigation bar when the fragment is destroyed
        bottomNav.visibility = View.VISIBLE
        // No navigation handling needed
    }
}
