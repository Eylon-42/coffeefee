package com.eaor.coffeefee.fragments

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.ImageAdapter
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class AddPostFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var bottomNav: View
    private var selectedLocation: LatLng? = null
    private val selectedImages = mutableListOf<Uri>()
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var placesClient: PlacesClient

    // Image picker result launcher
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
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
                    view?.findViewById<TextView>(R.id.selectedLocationText)?.text =
                        place.name.toString()
                }
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                Log.d("AddPostFragment", "User canceled autocomplete")
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_add_post, container, false)

        // Initialize Places Client
        placesClient = Places.createClient(requireContext())

        // Handle Bottom Navigation visibility
        bottomNav = requireActivity().findViewById(R.id.bottom_nav)
        bottomNav.visibility = View.GONE

        setupToolbar(view)
        setupLocationButton(view)
        setupImagePicker(view)
        setupPostButton(view)

        // Setup search functionality
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
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

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

                // If an image is selected, upload it
                if (selectedImages.isNotEmpty()) {
                    val imageUri = selectedImages[0] // Assuming one image is selected for now
                    uploadImageToStorage(imageUri,
                        onSuccess = { imageUrl ->
                            // Now post the post with the image URL
                            createPostInFirestore(experienceDescription, userId, imageUrl)
                        },
                        onFailure = { exception ->
                            Log.e("AddPostFragment", "Image upload failed: ${exception.message}")
                            Toast.makeText(requireContext(), "Image upload failed", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    // If no image is selected, just post without a photo
                    createPostInFirestore(experienceDescription, userId, null)
                }
            }
        }
    }

    private fun createPostInFirestore(experienceDescription: String, userId: String?, imageUrl: String?) {
        val postData = mapOf(
            "UserId" to userId,
            "experienceDescription" to experienceDescription,
            "location" to mapOf(
                "name" to view?.findViewById<TextView>(R.id.selectedLocationText)?.text.toString(),
                "latitude" to selectedLocation?.latitude,
                "longitude" to selectedLocation?.longitude
            ),
            "timestamp" to System.currentTimeMillis(),
            "photoUrl" to imageUrl // Attach the image URL (if any) to the post
        )

        db.collection("Posts")
            .add(postData)
            .addOnSuccessListener {
                Log.d("AddPostFragment", "Post added with ID: ${it.id}")
                Toast.makeText(requireContext(), "Post added successfully", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .addOnFailureListener { exception ->
                Log.e("AddPostFragment", "Error adding post: ${exception.message}")
                Toast.makeText(requireContext(), "Error adding post", Toast.LENGTH_SHORT).show()
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

    companion object {
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 1
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Restore the visibility of the bottom navigation bar when the fragment is destroyed
        bottomNav.visibility = View.VISIBLE
    }
}
