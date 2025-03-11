package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.eaor.coffeefee.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.squareup.picasso.Picasso

class CoffeeFragment : Fragment(), OnMapReadyCallback {
    private var coffeeName: String = ""
    private var coffeeLatitude: Float = 0f
    private var coffeeLongitude: Float = 0f
    private var isFavorite: Boolean = false
    private lateinit var googleMap: GoogleMap
    private lateinit var showLocationButton: Button
    private lateinit var coffeeImage: ImageView // Add reference to coffee image

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coffee, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the ImageView for the coffee image
        coffeeImage = view.findViewById(R.id.coffeeImage)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = ""

        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        val favoriteButton = view.findViewById<ImageButton>(R.id.favoriteButton)
        favoriteButton.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.coffee_primary)
        )

        favoriteButton.setOnClickListener {
            isFavorite = !isFavorite
            favoriteButton.setImageResource(
                if (isFavorite) R.drawable.ic_heart_filled
                else R.drawable.ic_heart_outline
            )
        }

        showLocationButton = view.findViewById(R.id.showLocationButton)
        arguments?.let { args ->
            coffeeName = args.getString("name", "")
            val description = args.getString("description", "")
            coffeeLatitude = args.getFloat("latitude", 0f)
            coffeeLongitude = args.getFloat("longitude", 0f)
            val imageUrl = args.getString("imageUrl", "") // Get the image URL from the arguments

            // Set the views based on the arguments
            view.findViewById<TextView>(R.id.toolbarTitle).text = coffeeName
            view.findViewById<TextView>(R.id.coffeeName).text = coffeeName
            view.findViewById<TextView>(R.id.descriptionText).text = description

            // Use Picasso to load the image URL into the ImageView
            if (imageUrl.isNotEmpty()) {
                Picasso.get()
                    .load(imageUrl) // The URL passed in the bundle
                    .into(coffeeImage) // Load the image into the ImageView
            }

            val mapFragment = childFragmentManager
                .findFragmentById(R.id.coffeeLocationMap) as SupportMapFragment
            mapFragment.getMapAsync(this)
        }

        showLocationButton.setOnClickListener {
            val bundle = Bundle().apply {
                putString("name", coffeeName)
            }
            findNavController().navigate(R.id.action_coffeeFragment_to_coffeeMapFragment, bundle)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        // Add a marker for the coffee shop location
        val coffeeLocation = LatLng(coffeeLatitude.toDouble(), coffeeLongitude.toDouble())
        val marker = googleMap.addMarker(MarkerOptions().position(coffeeLocation).title(coffeeName))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coffeeLocation, 15f))

        // Show the info window for the coffee shop marker
        marker?.showInfoWindow()
    }
}
