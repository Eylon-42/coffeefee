package com.eaor.coffeefee.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.eaor.coffeefee.MainActivity
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

class CoffeeMapFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap
    private lateinit var coffeeShops: List<CoffeeShop>
    private lateinit var descriptionOverlay: View
    private lateinit var locationNameTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var locationAddressTextView: TextView
    private lateinit var openInMapsButton: Button
    private lateinit var repository: CoffeeShopRepository
    private val scope = CoroutineScope(Dispatchers.Main)
    private val markers = mutableMapOf<String, CoffeeShop>()
    private var selectedCoffeeShop: CoffeeShop? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                enableMyLocation()
            }
            else -> {
                handleNoLocationPermission()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_coffee_map, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        repository = CoffeeShopRepository.getInstance(requireContext())

        // Initialize views
        descriptionOverlay = rootView.findViewById(R.id.descriptionOverlay)
        locationNameTextView = rootView.findViewById(R.id.locationNameTextView)
        descriptionTextView = rootView.findViewById(R.id.descriptionTextView)
        locationAddressTextView = rootView.findViewById(R.id.locationAddressTextView)
        openInMapsButton = rootView.findViewById(R.id.openInMapsButton)

        // Set up back button
        rootView.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }
        rootView.findViewById<TextView>(R.id.toolbarTitle).text = "Coffee Map"

        // Get the SupportMapFragment and request map when it's ready
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as? SupportMapFragment
            ?: throw IllegalStateException("Map Fragment not found")

        mapFragment.getMapAsync(this)

        // Set up the button click listener
        openInMapsButton.setOnClickListener {
            openLocationInMaps()
        }

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide bottom navigation when this fragment is displayed
        val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
        bottomNav?.visibility = View.GONE

        // Get selected coffee shop from arguments if available
        arguments?.let { args ->
            val placeId = args.getString("placeId")
            val name = args.getString("name")
            val photoUrl = args.getString("photoUrl")
            val rating = if (args.containsKey("rating")) args.getFloat("rating") else null
            
            if (placeId != null && name != null) {
                selectedCoffeeShop = CoffeeShop(
                    name = name,
                    rating = rating,
                    description = "",  // Not needed for map
                    latitude = 0.0,  // Will be updated from repository
                    longitude = 0.0,  // Will be updated from repository
                    placeId = placeId,
                    photoUrl = photoUrl
                )
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.setOnMarkerClickListener(this)
        
        // Enable zoom controls and my location button
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true

        // Add map click listener to hide description overlay
        googleMap.setOnMapClickListener {
            descriptionOverlay.visibility = View.GONE
        }

        // Check location permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }

        // Load all coffee shops
        loadCoffeeShops()
    }

    private fun loadCoffeeShops() {
        scope.launch {
            try {
                val shops = repository.getAllCoffeeShops()
                
                // Clear existing markers
                googleMap.clear()
                markers.clear()

                // Add markers for all coffee shops
                shops.forEach { shop ->
                    val position = LatLng(shop.latitude, shop.longitude)
                    val marker = googleMap.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(shop.name)
                    )
                    marker?.let { markers[it.id] = shop }

                    // If this is the selected coffee shop, zoom to it and show overlay
                    if (shop.placeId == selectedCoffeeShop?.placeId) {
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
                        marker?.showInfoWindow()
                        
                        // Update and show the description overlay
                        locationNameTextView.text = shop.name
                        descriptionTextView.text = shop.description
                        locationAddressTextView.text = shop.address ?: "Address not available"
                        descriptionOverlay.visibility = View.VISIBLE
                    }
                }

                // If no specific shop is selected, get current location
                if (selectedCoffeeShop == null) {
                    if (hasLocationPermissions()) {
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                location?.let {
                                    val currentLocation = LatLng(it.latitude, it.longitude)
                                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f))
                                } ?: run {
                                    // If location is null, show all markers
                                    showAllMarkers(shops)
                                }
                            }
                        } catch (e: SecurityException) {
                            Log.e("CoffeeMapFragment", "Error getting location", e)
                            showAllMarkers(shops)
                        }
                    } else {
                        checkLocationPermission()
                        showAllMarkers(shops)
                    }
                }
            } catch (e: Exception) {
                Log.e("CoffeeMapFragment", "Error loading coffee shops", e)
            }
        }
    }

    private fun showAllMarkers(coffeeShops: List<CoffeeShop>) {
        if (coffeeShops.isNotEmpty()) {
            val builder = com.google.android.gms.maps.model.LatLngBounds.Builder()
            coffeeShops.forEach { shop ->
                builder.include(LatLng(shop.latitude, shop.longitude))
            }
            val bounds = builder.build()
            val padding = 100 // offset from edges of the map in pixels
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            googleMap.moveCamera(cameraUpdate)
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        // Show info window
        marker.showInfoWindow()
        
        // Get the coffee shop associated with this marker
        val coffeeShop = markers[marker.id]
        if (coffeeShop != null) {
            // Zoom to the marker
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(marker.position, 15f)
            )

            // Update and show the description overlay
            locationNameTextView.text = coffeeShop.name
            descriptionTextView.text = coffeeShop.description
            locationAddressTextView.text = coffeeShop.address ?: "Address not available"
            descriptionOverlay.visibility = View.VISIBLE
        }
        
        return true
    }

    private fun checkLocationPermission() {
        when {
            hasLocationPermissions() -> {
                enableMyLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs location permission to show your position on the map and find coffee shops near you.")
                    .setPositiveButton("OK") { _, _ ->
                        requestLocationPermissions()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        handleNoLocationPermission()
                    }
                    .create()
                    .show()
            }
            else -> {
                requestLocationPermissions()
            }
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun enableMyLocation() {
        if (hasLocationPermissions()) {
            try {
                googleMap.isMyLocationEnabled = true
                requestLocationUpdate()
            } catch (e: SecurityException) {
                Log.e("CoffeeMapFragment", "Error enabling location", e)
            }
        }
    }

    private fun handleNoLocationPermission() {
        Toast.makeText(
            requireContext(),
            "Location permission not granted. Some features may be limited.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun requestLocationUpdate() {
        Toast.makeText(
            requireContext(),
            "Waiting for location...",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openLocationInMaps() {
        val name = locationNameTextView.text.toString()
        val coffeeShop = markers.values.find { it.name == name }
        
        if (coffeeShop != null) {
            val uri = "geo:${coffeeShop.latitude},${coffeeShop.longitude}?q=${coffeeShop.latitude},${coffeeShop.longitude}($name)"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "Could not find location coordinates", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        // Properly cleanup map resources to prevent database locks
        if (::googleMap.isInitialized) {
            try {
                googleMap.clear()
                googleMap.setMapType(GoogleMap.MAP_TYPE_NONE)
                
                // Remove all listeners to prevent callbacks after fragment is destroyed
                googleMap.setOnMarkerClickListener(null)
                googleMap.setOnMapClickListener(null)
                
                // Force release map resources
                val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
                mapFragment?.onDestroyView()
            } catch (e: Exception) {
                Log.e("CoffeeMapFragment", "Error cleaning up map resources", e)
            }
        }
        
        // Restore bottom navigation visibility when leaving this fragment
        val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
        bottomNav?.visibility = View.VISIBLE
        
        super.onDestroyView()
    }
}
