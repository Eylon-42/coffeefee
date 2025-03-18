package com.eaor.coffeefee.fragments
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.firebase.firestore.FieldValue
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.FeedItem
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso
import com.eaor.coffeefee.MainActivity

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

        db = FirebaseFirestore.getInstance()
        
        setupToolbar(view)
        loadPostData(view)
        setupButtons(view)
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
            uploadImageAndUpdatePost(updateData)
        } else {
            updatePostInFirestore(updateData)
        }
    }

    private fun uploadImageAndUpdatePost(updateData: HashMap<String, Any>) {
        val storageRef = FirebaseStorage.getInstance().reference.child("post_images/$postId.jpg")

        storageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    updateData["photoUrl"] = uri.toString()
                    updatePostInFirestore(updateData)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error uploading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updatePostInFirestore(updateData: HashMap<String, Any>) {
        db.collection("Posts").document(postId)
            .update(updateData)
            .addOnSuccessListener {
                Toast.makeText(context, "Post updated successfully", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error updating post: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    selectedPlaceDescription = document.getString("caption")
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
                val coffeeShopData = hashMapOf(
                    "name" to (selectedPlaceName ?: "Unnamed Coffee Shop"),
                    "rating" to selectedPlaceRating,
                    "caption" to (selectedPlaceDescription ?: "Come visit our cozy coffee shop and enjoy a perfect cup of coffee!"),
                    "latitude" to (selectedLocation?.latitude ?: 0.0),
                    "longitude" to (selectedLocation?.longitude ?: 0.0),
                    "placeId" to selectedPlaceId,
                    "address" to (selectedPlaceAddress ?: "Address not available"),
                    "photoUrl" to photoUrl,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                
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
                // Create a FetchPhotoRequest
                val photoRequest = FetchPhotoRequest.builder(photoMetadata).build()
                MainActivity.placesClient.fetchPhoto(photoRequest).addOnSuccessListener { fetchPhotoResponse ->
                    // Get the bitmap from the photo response
                    val bitmap = fetchPhotoResponse.bitmap
                    
                    // Upload to Firebase Storage and get URL
                    uploadPhotoToFirebase(bitmap, placeId) { url ->
                        callback(url)
                    }
                }.addOnFailureListener { e ->
                    Log.e("EditPostFragment", "Error fetching place photo: ${e.message}")
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

    private fun uploadPhotoToFirebase(bitmap: Bitmap, placeId: String, callback: (String?) -> Unit) {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference.child("coffee_shops/$placeId.jpg")
        
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val data = baos.toByteArray()
        
        storageRef.putBytes(data).continueWithTask { task ->
            if (!task.isSuccessful) {
                throw task.exception ?: Exception("Upload failed")
            }
            storageRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                callback(task.result.toString())
            } else {
                Log.e("EditPostFragment", "Error uploading photo: ${task.exception?.message}")
                callback(null)
            }
        }
    }
}
