package com.eaor.coffeefee.fragments

import com.eaor.coffeefee.BuildConfig
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.ImageAdapter
import com.eaor.coffeefee.models.CoffeeShop
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.firestore.FirebaseFirestore

class AddPostFragment : Fragment() {

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
    private lateinit var db: FirebaseFirestore

    // Image picker result launcher
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImages.add(it)
            updateImagesRecyclerView()
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
                    
                    // Fetch place details to get rating and photo
                    fetchPlaceDetails(place.id)
                }
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                Log.d("AddPostFragment", "User canceled autocomplete")
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_add_post, container, false)

        // Initialize Firebase
        db = FirebaseFirestore.getInstance()

        // Initialize Places Client
        placesClient = Places.createClient(requireContext())

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
        view.findViewById<Button>(R.id.postButton).setOnClickListener {
            if (selectedLocation == null || selectedPlaceName == null) {
                Toast.makeText(requireContext(), "Please select a coffee shop location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create coffee shop data
            val coffeeShopData = hashMapOf(
                "name" to selectedPlaceName,
                "rating" to selectedPlaceRating,
                "caption" to (selectedPlaceDescription ?: ""),
                "latitude" to selectedLocation!!.latitude,
                "longitude" to selectedLocation!!.longitude,
                "placeId" to selectedPlaceId,
                "photoUrl" to selectedPlacePhotoUrl,
                "address" to (selectedPlaceAddress ?: "")
            )

            // Add to Firestore
            db.collection("CoffeeShops")
                .document(selectedPlaceId!!)
                .set(coffeeShopData)
                .addOnSuccessListener {
                    Log.d("AddPostFragment", "Coffee shop added successfully")
                    findNavController().navigateUp()
                }
                .addOnFailureListener { e ->
                    Log.e("AddPostFragment", "Error adding coffee shop", e)
                    Toast.makeText(requireContext(), "Failed to add coffee shop: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
        val fields = listOf(
            Place.Field.RATING,
            Place.Field.PHOTO_METADATAS,
            Place.Field.EDITORIAL_SUMMARY,
            Place.Field.ADDRESS
        )
        
        // First check if we have the coffee shop data in Firestore
        db.collection("CoffeeShops")
            .document(placeId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Use existing data
                    selectedPlaceRating = document.getDouble("rating")?.toFloat()
                    selectedPlacePhotoUrl = document.getString("photoUrl")
                    selectedPlaceDescription = document.getString("caption")
                    selectedPlaceAddress = document.getString("address")
                    Log.d("AddPostFragment", "Using existing coffee shop data")
                } else {
                    // Fetch from Places API if not in Firestore
                    placesClient.fetchPlace(
                        com.google.android.libraries.places.api.net.FetchPlaceRequest.newInstance(placeId, fields)
                    ).addOnSuccessListener { response ->
                        val place = response.place
                        selectedPlaceRating = place.rating?.toFloat()
                        selectedPlaceAddress = place.address
                        
                        // Get the editorial summary or use default description
                        selectedPlaceDescription = place.editorialSummary?.toString()
                            ?: "Come visit our cozy coffee shop and enjoy a perfect cup of coffee!"
                        
                        // Get the photo URL if available
                        selectedPlacePhotoUrl = place.photoMetadatas?.firstOrNull()?.let {
                            "https://maps.googleapis.com/maps/api/place/photo?maxwidth=400&photo_reference=${it.zza()}&key=${BuildConfig.GOOGLE_MAPS_API_KEY}"
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("AddPostFragment", "Error checking for existing coffee shop", e)
                // Fallback to Places API
                placesClient.fetchPlace(
                    com.google.android.libraries.places.api.net.FetchPlaceRequest.newInstance(placeId, fields)
                ).addOnSuccessListener { response ->
                    val place = response.place
                    selectedPlaceRating = place.rating?.toFloat()
                    selectedPlaceAddress = place.address
                    selectedPlaceDescription = place.editorialSummary?.toString()
                        ?: "Come visit our cozy coffee shop and enjoy a perfect cup of coffee!"
                    
                    selectedPlacePhotoUrl = place.photoMetadatas?.firstOrNull()?.let {
                        "https://maps.googleapis.com/maps/api/place/photo?maxwidth=400&photo_reference=${it.zza()}&key=${BuildConfig.GOOGLE_MAPS_API_KEY}"
                    }
                }
            }
    }

    companion object {
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 1
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bottomNav.visibility = View.VISIBLE
    }
}
