package com.eaor.coffeefee.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory

class CoffeeMapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted
                enableMyLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_coffee_map, container, false)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Initialize Maps SDK
        MapsInitializer.initialize(requireContext())

        // Set up back button
        rootView.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        // Get the SupportMapFragment and request map when it's ready
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        return rootView
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Enable zoom controls
        map.uiSettings.isZoomControlsEnabled = true
        
        // Add coffee shop markers
        val coffeeShops = listOf(
            CoffeeShop("Cafe Dizengoff", 4.5f, "Modern cafe in the heart of Tel Aviv", 32.0853, 34.7818),
            CoffeeShop("Jerusalem Coffee House", 4.3f, "Traditional cafe near Mahane Yehuda", 31.7767, 35.2345),
            CoffeeShop("Haifa Bay Cafe", 4.4f, "Scenic coffee shop with bay views", 32.7940, 34.9896),
            CoffeeShop("Desert Bean", 4.2f, "Cozy spot in Beer Sheva's Old City", 31.2516, 34.7913),
            CoffeeShop("Marina Coffee", 4.6f, "Luxurious cafe by the Herzliya Marina", 32.1877, 34.8702),
            CoffeeShop("Sarona Coffee Works", 4.7f, "Trendy cafe in Sarona Market", 32.0731, 34.7925)
        )

        for (shop in coffeeShops) {
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(shop.latitude, shop.longitude))
                    .title(shop.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            )
        }

        // Check location permissions
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        when {
            hasLocationPermissions() -> {
                enableMyLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show rationale and request permission
                Toast.makeText(
                    requireContext(),
                    "Location permission is needed to show your location",
                    Toast.LENGTH_SHORT
                ).show()
                requestLocationPermissions()
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
        try {
            googleMap?.isMyLocationEnabled = true
            // Get last known location and move camera
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    googleMap?.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(userLatLng, 12f)
                    )
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
}
