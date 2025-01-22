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
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.CoffeeShop
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
import java.util.Locale

class CoffeeMapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap
    private lateinit var coffeeShops: List<CoffeeShop>
    private lateinit var descriptionOverlay: View
    private lateinit var locationNameTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var locationAddressTextView: TextView
    private lateinit var openInMapsButton: Button

    // Create a mutable list to hold markers
    private val markers = mutableListOf<Marker>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                // Precise location access granted
                enableMyLocation()
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // Only approximate location access granted
                enableMyLocation()
            }
            else -> {
                // No location access granted
                Toast.makeText(
                    requireContext(),
                    "Location permission is required to show your location",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_coffee_map, container, false)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

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

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Enable zoom controls and my location button
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true
        // Always enable the My Location button
        googleMap.isMyLocationEnabled = true
        
        // Add coffee shop markers
        googleMap.clear()
        addCoffeeShopMarkers()
        
        // Check location permissions and handle accordingly
        checkLocationPermission { permissionsGranted ->
            if (permissionsGranted) {
                handleMapCameraPosition()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Location permission is required to show your location",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        googleMap.setOnMarkerClickListener { clickedMarker ->
            val coffeeShop = clickedMarker.tag as? CoffeeShop
            coffeeShop?.let {
                // Populate the overlay with data
                locationNameTextView.text = it.name
                descriptionTextView.text = it.caption
                locationAddressTextView.text = it.address ?: "Address not found"

                // Show the overlay
                descriptionOverlay.visibility = View.VISIBLE

                // Show the info window for the clicked marker
                clickedMarker.showInfoWindow()
            }
            true
        }

        // Hide overlay when the map is clicked
        googleMap.setOnMapClickListener {
            descriptionOverlay.visibility = View.GONE
        }
    }

    private fun addCoffeeShopMarkers() {
        coffeeShops = listOf(
            CoffeeShop("Cafe Dizengoff", 4.5f, "Modern cafe in the heart of Tel Aviv", 32.0853, 34.7818),
            CoffeeShop("Jerusalem Coffee House", 4.3f, "Traditional cafe near Mahane Yehuda", 31.7767, 35.2345),
            CoffeeShop("Haifa Bay Cafe", 4.4f, "Scenic coffee shop with bay views", 32.7940, 34.9896),
            CoffeeShop("Desert Bean", 4.2f, "Cozy spot in Beer Sheva's Old City", 31.2516, 34.7913),
            CoffeeShop("Marina Coffee", 4.6f, "Luxurious cafe by the Herzliya Marina", 32.1877, 34.8702),
            CoffeeShop("Sarona Coffee Works", 4.7f, "Trendy cafe in Sarona Market", 32.0731, 34.7925)
        )

        val geocoder = Geocoder(requireContext(), Locale.getDefault())

        for (shop in coffeeShops) {
            val location = LatLng(shop.latitude, shop.longitude)
            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(location)
                    .title(shop.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            )
            marker?.tag = shop
            marker?.let { markers.add(it) } // Add marker to the list

            // Fetch the address
            try {
                val addresses = geocoder.getFromLocation(shop.latitude, shop.longitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    shop.address = addresses[0]?.getAddressLine(0) // Get the full address
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Move camera to the first coffee shop
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(coffeeShops[0].latitude, coffeeShops[0].longitude), 12f))
    }

    private fun checkLocationPermission(callback: (Boolean) -> Unit) {
        when {
            hasLocationPermissions() -> {
                callback(true)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show rationale and request permission
                Toast.makeText(
                    requireContext(),
                    "Location permission is needed to show your location",
                    Toast.LENGTH_SHORT
                ).show()
                requestLocationPermissions(callback)
            }
            else -> {
                requestLocationPermissions(callback)
            }
        }
    }

    private fun requestLocationPermissions(callback: (Boolean) -> Unit) {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun handleMapCameraPosition() {
        arguments?.let { args ->
            val coffeeShopName = args.getString("name", "") // Get the coffee shop name
            
            if (coffeeShopName.isNotEmpty()) {
                val coffeeShop = coffeeShops.find { it.name == coffeeShopName }

                coffeeShop?.let {
                    val selectedLatLng = LatLng(it.latitude, it.longitude)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 12f))

                    // Populate the overlay with data
                    locationNameTextView.text = it.name
                    descriptionTextView.text = it.caption
                    locationAddressTextView.text = it.address ?: "Address not found"
                    
                    // Show the overlay
                    descriptionOverlay.visibility = View.VISIBLE

                    // Show the info window for the corresponding marker
                    markers.find { marker -> marker.tag == it }?.showInfoWindow()
                }
            } else {
                enableMyLocation()
            }
        } ?: run {
            enableMyLocation()
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

    private fun enableMyLocation() {
        try {
            // Get last known location and move camera
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 12f))
                }
            }
        } catch (e: SecurityException) {
            // Handle the case where permission is not granted
            Toast.makeText(
                requireContext(),
                "Location permission is required to show your location",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openLocationInMaps() {
        val coffeeShop = coffeeShops.find { it.name == locationNameTextView.text }
        coffeeShop?.let {
            val uri = "geo:${it.latitude},${it.longitude}?q=${it.latitude},${it.longitude}(${it.name})"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            startActivity(intent)
        }
    }
}
