package com.eaor.coffeefee.fragments
import com.squareup.picasso.Callback
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.CoffeeShopAdapter
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CoffeeFragment : Fragment(), OnMapReadyCallback {
    private var coffeeName: String = ""
    private var coffeeLatitude: Float = 0f
    private var coffeeLongitude: Float = 0f
    private var isFavorite: Boolean = false
    private lateinit var googleMap: GoogleMap
    private lateinit var showLocationButton: Button
    private var placeId: String? = null
    private var photoUrl: String? = null
    private var rating: Float? = null
    private val repository = CoffeeShopRepository.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: CoffeeShopAdapter
    private var recyclerView: RecyclerView? = null
    private lateinit var coffeeImage: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_coffee, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        coffeeImage = view.findViewById(R.id.coffeeImage)
        setupToolbar(view)

        showLocationButton = view.findViewById(R.id.showLocationButton)
        arguments?.let { args ->
            coffeeName = args.getString("name", "")
            val description = args.getString("description", "")
            coffeeLatitude = args.getFloat("latitude", 0f)
            coffeeLongitude = args.getFloat("longitude", 0f)
            placeId = args.getString("placeId")
            photoUrl = args.getString("photoUrl")
            rating = if (args.containsKey("rating")) args.getFloat("rating") else null
            val address = args.getString("address")
            val imageUrl = args.getString("imageUrl", "")

           
            view.findViewById<TextView>(R.id.toolbarTitle).text = coffeeName.takeIf { it.isNotEmpty() } 
                ?: "Unnamed Coffee Shop"
            view.findViewById<TextView>(R.id.coffeeName).text = coffeeName.takeIf { it.isNotEmpty() } 
                ?: "Unnamed Coffee Shop"
            view.findViewById<TextView>(R.id.descriptionText).text = 
                if (description.isNotEmpty()) description else "No description available"
            view.findViewById<TextView>(R.id.coffeeAddress).text = 
                if (!address.isNullOrEmpty()) address else "Address not available"

            setupRatingDisplay(view, rating)
            loadCoffeeShopPhoto(view)

            if (imageUrl.isNotEmpty()) {
                Picasso.get()
                    .load(imageUrl)
                    .into(coffeeImage)
            }

            val mapFragment = childFragmentManager
                .findFragmentById(R.id.coffeeLocationMap) as SupportMapFragment
            mapFragment.getMapAsync(this)
        }
        
        showLocationButton.setOnClickListener {
            val bundle = Bundle().apply {
                putString("name", coffeeName)
                putString("placeId", placeId)
                putString("photoUrl", photoUrl)
                if (rating != null) putFloat("rating", rating!!)
            }
            findNavController().navigate(R.id.action_coffeeFragment_to_coffeeMapFragment, bundle)
        }

        setupRecyclerView()
        loadMissingDetails()
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)

        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }
    }

    /**
     * Loads the coffee shop photo with enhanced caching and error handling
     */
    private fun loadCoffeeShopPhoto(view: View) {
        val coffeeImage = view.findViewById<ImageView>(R.id.coffeeImage)

        try {
            if (!photoUrl.isNullOrEmpty()) {
                Log.d("CoffeeFragment", "Loading coffee shop photo from URL: $photoUrl")
                
                Picasso.get()
                    .load(photoUrl)
                    .placeholder(R.drawable.placeholder)  // Use placeholder.jpg directly
                    .error(R.drawable.placeholder)        // Use placeholder.jpg directly
                    .fit()
                    .centerCrop()
                    .into(coffeeImage)
            } else {
                Log.d("CoffeeFragment", "No photo URL available, showing placeholder")
                
                // Directly set the placeholder image
                coffeeImage.setImageResource(R.drawable.placeholder)
            }
        } catch (e: Exception) {
            Log.e("CoffeeFragment", "Exception loading photo", e)
            coffeeImage.setImageResource(R.drawable.placeholder)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map

        if (coffeeLatitude != 0f && coffeeLongitude != 0f) {
            val coffeeLocation = LatLng(coffeeLatitude.toDouble(), coffeeLongitude.toDouble())
            val marker = googleMap.addMarker(MarkerOptions().position(coffeeLocation).title(coffeeName))
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coffeeLocation, 15f))
            marker?.showInfoWindow()
        }

        googleMap.uiSettings.isZoomControlsEnabled = true
    }

    private fun setupRatingDisplay(view: View, rating: Float?) {
        // Get references to all heart images
        val heart1 = view.findViewById<ImageView>(R.id.heart1)
        val heart2 = view.findViewById<ImageView>(R.id.heart2)
        val heart3 = view.findViewById<ImageView>(R.id.heart3)
        val heart4 = view.findViewById<ImageView>(R.id.heart4)
        val heart5 = view.findViewById<ImageView>(R.id.heart5)
        
        // List of all hearts for easy iteration
        val hearts = listOf(heart1, heart2, heart3, heart4, heart5)
        
        // Default - set all hearts to empty outline
        hearts.forEach { heart ->
            heart.setImageResource(R.drawable.ic_heart_outline)
            // Use the same color filter as in CoffeeShopAdapter
            heart.setColorFilter(ContextCompat.getColor(requireContext(), R.color.coffee_primary))
        }
        
        if (rating == null || rating <= 0) {
            // If no rating, leave all hearts as outlines
            Log.d("CoffeeFragment", "No rating available")
        } else {
            // Fill hearts based on rating (1-5)
            Log.d("CoffeeFragment", "Rating: $rating")
            
            // Calculate how many full hearts to show (integer part)
            val flooredRating = rating.toInt()
            val hasHalf = (rating - flooredRating) >= 0.5
            
            // Update each heart based on the rating
            hearts.forEachIndexed { index, heart ->
                val resource = when {
                    index < flooredRating -> R.drawable.ic_heart_filled
                    index == flooredRating && hasHalf -> R.drawable.ic_heart_half
                    else -> R.drawable.ic_heart_outline
                }
                heart.setImageResource(resource)
                heart.setColorFilter(ContextCompat.getColor(requireContext(), R.color.coffee_primary))
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView?.let { rv ->
            rv.layoutManager = LinearLayoutManager(context)
            adapter = CoffeeShopAdapter(emptyList(), showCaptions = true)
            adapter.setOnItemClickListener { coffeeShop ->
                val bundle = Bundle().apply {
                    putString("placeId", coffeeShop.placeId)
                    putString("name", coffeeShop.name)
                    putString("photoUrl", coffeeShop.photoUrl)
                    coffeeShop.rating?.let { putFloat("rating", it) }
                }

                findNavController().navigate(
                    R.id.action_coffeeFragment_to_coffeeMapFragment,
                    bundle
                )
            }
            rv.adapter = adapter
        }
    }

    private fun loadMissingDetails() {
        if (placeId != null) {
            // If we have placeId, use it directly
            scope.launch {
                try {
                    val coffeeShop = repository.getCoffeeShop(placeId!!)
                    if (coffeeShop != null) {
                        withContext(Dispatchers.Main) {
                            updateUIWithCoffeeShop(coffeeShop)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CoffeeFragment", "Error loading details by placeId", e)
                    // Fallback to name search if placeId fails
                    loadDetailsByName()
                }
            }
        } else {
            // Fallback to name search if no placeId
            loadDetailsByName()
        }
    }

    private fun loadDetailsByName() {
        if (coffeeName.isNotEmpty()) {
            scope.launch {
                try {
                    val coffeeShop = repository.getCoffeeShopByName(coffeeName)
                    if (coffeeShop != null) {
                        withContext(Dispatchers.Main) {
                            updateUIWithCoffeeShop(coffeeShop)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CoffeeFragment", "Error loading details by name", e)
                }
            }
        }
    }

    private fun updateUIWithCoffeeShop(coffeeShop: CoffeeShop) {
        placeId = coffeeShop.placeId
        photoUrl = coffeeShop.photoUrl
        rating = coffeeShop.rating
        
        // Update UI with new details
        setupRatingDisplay(requireView(), rating)
        loadCoffeeShopPhoto(requireView())
        view?.findViewById<TextView>(R.id.coffeeAddress)?.text = 
            coffeeShop.address ?: "Address not available"
    }

    private fun updateCoffeeShopInfo(view: View) {
        // Set coffee shop name with fallback
        view.findViewById<TextView>(R.id.toolbarTitle).text = coffeeName.takeIf { it.isNotEmpty() } 
            ?: "Unnamed Coffee Shop"
        view.findViewById<TextView>(R.id.coffeeName).text = coffeeName.takeIf { it.isNotEmpty() } 
            ?: "Unnamed Coffee Shop"
        
        // Set description with fallback
        val description = arguments?.getString("description") ?: "No description available"
        view.findViewById<TextView>(R.id.descriptionText).text = 
            if (description.isNotEmpty()) description else "No description available"
        
        // Set address with fallback
        val address = arguments?.getString("address")
        view.findViewById<TextView>(R.id.coffeeAddress).text = 
            if (!address.isNullOrEmpty()) address else "Address not available"
        
        // Handle rating display
        setupRatingDisplay(view, rating)
        
        // Load photo
        loadCoffeeShopPhoto(view)
    }
} 