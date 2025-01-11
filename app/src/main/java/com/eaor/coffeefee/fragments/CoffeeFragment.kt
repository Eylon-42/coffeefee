package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.eaor.coffeefee.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class CoffeeFragment : Fragment(), OnMapReadyCallback {
    private var isFavorite = false
    private var coffeeName = ""
    private var coffeeLatitude: Float = 0f
    private var coffeeLongitude: Float = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coffee, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = ""
        
        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        val favoriteButton = view.findViewById<ImageButton>(R.id.favoriteButton)
        favoriteButton.setOnClickListener {
            isFavorite = !isFavorite
        }

        arguments?.let { args ->
            coffeeName = args.getString("name", "")
            val description = args.getString("description", "")
            coffeeLatitude = args.getFloat("latitude", 0f)
            coffeeLongitude = args.getFloat("longitude", 0f)

            view.findViewById<TextView>(R.id.toolbarTitle).text = coffeeName
            view.findViewById<TextView>(R.id.coffeeName).text = coffeeName
            view.findViewById<TextView>(R.id.descriptionText).text = description

            val mapFragment = childFragmentManager
                .findFragmentById(R.id.coffeeLocationMap) as SupportMapFragment
            
            // Enable lite mode through bundle
            val bundle = Bundle().apply {
                putBoolean("lite_mode", true)
            }
            mapFragment.arguments = bundle
            
            mapFragment.getMapAsync(this)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        val coffeeLocation = LatLng(coffeeLatitude.toDouble(), coffeeLongitude.toDouble())
        googleMap.addMarker(MarkerOptions().position(coffeeLocation).title(coffeeName))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coffeeLocation, 15f))
    }
} 