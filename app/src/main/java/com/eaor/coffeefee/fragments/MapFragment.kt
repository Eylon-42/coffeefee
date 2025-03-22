package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.eaor.coffeefee.R
import com.eaor.coffeefee.viewmodels.CoffeeViewModel
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment

class MapFragment : Fragment(), OnMapReadyCallback {
    
    private lateinit var coffeeViewModel: CoffeeViewModel
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coffee_map, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        coffeeViewModel = ViewModelProvider(this)[CoffeeViewModel::class.java]
        coffeeViewModel.initialize(requireContext())
        
        // Force refresh coffee shops data from Firestore when map is displayed
        coffeeViewModel.refreshCoffeeShops()
        
        // Set up map when view is created
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    
    override fun onMapReady(googleMap: GoogleMap) {
        // Map is ready to be used
        // Configure map settings to reduce resource usage and prevent locks
        try {
            // Set map type to normal to reduce tile caching
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)
            
            // Limit zoom level to reduce tile cache size
            googleMap.setMaxZoomPreference(16f)
            
            // Enable only essential UI controls
            googleMap.uiSettings.apply {
                isZoomControlsEnabled = true
                isCompassEnabled = true
                isMyLocationButtonEnabled = true
                isMapToolbarEnabled = false
                isRotateGesturesEnabled = true
                isScrollGesturesEnabled = true
                isTiltGesturesEnabled = false
            }
        } catch (e: Exception) {
            // Log error but don't crash
            android.util.Log.e("MapFragment", "Error configuring map", e)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Clean up map resources to prevent database locks
        try {
            val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
            mapFragment?.onDestroyView()
        } catch (e: Exception) {
            android.util.Log.e("MapFragment", "Error cleaning up map resources", e)
        }
    }
} 