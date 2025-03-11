package com.eaor.coffeefee.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.CoffeeShopAdapter
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.squareup.picasso.Picasso
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide
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
    private lateinit var coffeeImage: ImageView // Add reference to coffee image

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

        // Initialize the ImageView for the coffee image
        coffeeImage = view.findViewById(R.id.coffeeImage)

        setupToolbar(view)

        val favoriteButton = view.findViewById<ImageButton>(R.id.favoriteButton)
        favoriteButton.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.coffee_primary)
        )

        favoriteButton.setOnClickListener {
            toggleFavorite(favoriteButton)
        }

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
            val imageUrl = args.getString("imageUrl", "") // Get the image URL from the arguments

            // Set the views based on the arguments
            view.findViewById<TextView>(R.id.toolbarTitle).text = coffeeName
            view.findViewById<TextView>(R.id.coffeeName).text = coffeeName
            view.findViewById<TextView>(R.id.descriptionText).text = description
            view.findViewById<TextView>(R.id.coffeeAddress).text = address ?: "Address not available"

            // Set up rating display
            setupRatingDisplay(view, rating)

            // Load coffee shop photo
            loadCoffeeShopPhoto(view)

            // Use Picasso to load the image URL into the ImageView
            if (imageUrl.isNotEmpty()) {
                Picasso.get()
                    .load(imageUrl) // The URL passed in the bundle
                    .into(coffeeImage) // Load the image into the ImageView
            }

            val mapFragment = childFragmentManager
                .findFragmentById(R.id.coffeeLocationMap) as SupportMapFragment
            mapFragment.getMapAsync(this)

            // Check if this coffee shop is in user's favorites
            checkFavoriteStatus(favoriteButton)
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
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)

        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun loadCoffeeShopPhoto(view: View) {
        val imageView = view.findViewById<ImageView>(R.id.coffeeImage)
        if (photoUrl != null) {
            Glide.with(this)
                .load(photoUrl)
                .placeholder(R.drawable.default_coffee_shop)
                .error(R.drawable.default_coffee_shop)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.default_coffee_shop)
        }
    }

    private fun checkFavoriteStatus(favoriteButton: ImageButton) {
        val userId = auth.currentUser?.uid ?: return
        placeId?.let { id ->
            scope.launch {
                try {
                    val doc = db.collection("users")
                        .document(userId)
                        .collection("favorites")
                        .document(id)
                        .get()
                        .await()

                    withContext(Dispatchers.Main) {
                        isFavorite = doc.exists()
                        updateFavoriteButton(favoriteButton)
                    }
                } catch (e: Exception) {
                    Log.e("CoffeeFragment", "Error checking favorite status", e)
                }
            }
        }
    }

    private fun toggleFavorite(favoriteButton: ImageButton) {
        val userId = auth.currentUser?.uid ?: return
        placeId?.let { id ->
            scope.launch {
                try {
                    val favoriteRef = db.collection("users")
                        .document(userId)
                        .collection("favorites")
                        .document(id)

                    if (isFavorite) {
                        favoriteRef.delete().await()
                    } else {
                        favoriteRef.set(mapOf(
                            "timestamp" to System.currentTimeMillis()
                        )).await()
                    }

                    withContext(Dispatchers.Main) {
                        isFavorite = !isFavorite
                        updateFavoriteButton(favoriteButton)
                    }
                } catch (e: Exception) {
                    Log.e("CoffeeFragment", "Error toggling favorite", e)
                    Toast.makeText(context, "Failed to update favorite status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateFavoriteButton(favoriteButton: ImageButton) {
        favoriteButton.setImageResource(
            if (isFavorite) R.drawable.ic_heart_filled
            else R.drawable.ic_heart_outline
        )
    }

    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map

        if (coffeeLatitude != 0f && coffeeLongitude != 0f) {
            // Add a marker for the coffee shop location
            val coffeeLocation = LatLng(coffeeLatitude.toDouble(), coffeeLongitude.toDouble())
            val marker = googleMap.addMarker(MarkerOptions().position(coffeeLocation).title(coffeeName))
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coffeeLocation, 15f))
            marker?.showInfoWindow()
        }

        // Enable zoom controls
        googleMap.uiSettings.isZoomControlsEnabled = true
    }

    private fun setupRatingDisplay(view: View, rating: Float?) {
        val ratingHearts = listOf<ImageView>(
            view.findViewById(R.id.heart1),
            view.findViewById(R.id.heart2),
            view.findViewById(R.id.heart3),
            view.findViewById(R.id.heart4),
            view.findViewById(R.id.heart5)
        )

        rating?.let { r ->
            val flooredRating = r.toInt()
            val hasHalf = (r - flooredRating) >= 0.5

            ratingHearts.forEachIndexed { index, heart ->
                val resource = when {
                    index < flooredRating -> R.drawable.ic_heart_filled
                    index == flooredRating && hasHalf -> R.drawable.ic_heart_half
                    else -> R.drawable.ic_heart_outline
                }
                heart.setImageResource(resource)
                heart.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.coffee_primary)
                )
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView?.let { rv ->
            rv.layoutManager = LinearLayoutManager(context)
            adapter = CoffeeShopAdapter(emptyList(), showCaptions = true)
            adapter.setOnItemClickListener { coffeeShop ->
                // Create bundle with coffee shop data
                val bundle = Bundle().apply {
                    putString("placeId", coffeeShop.placeId)
                    putString("name", coffeeShop.name)
                    putString("photoUrl", coffeeShop.photoUrl)
                    coffeeShop.rating?.let { putFloat("rating", it) }
                }

                // Navigate to map fragment with the bundle
                findNavController().navigate(
                    R.id.action_coffeeFragment_to_coffeeMapFragment,
                    bundle
                )
            }
            rv.adapter = adapter
        }
    }
} 