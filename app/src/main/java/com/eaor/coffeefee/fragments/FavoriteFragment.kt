package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.FavoriteCoffeeAdapter
import com.eaor.coffeefee.models.CoffeeShop

class FavoriteFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_favorite, container, false)

        // Initialize Toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)

        // Initialize RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Sample data - replace with actual favorite coffee shops
        val favoriteCoffeeShops = listOf(
            CoffeeShop("Cafe Dizengoff", 4.5f, "Modern cafe in the heart of Tel Aviv", 32.0853, 34.7818),
            CoffeeShop("Jerusalem Coffee House", 4.3f, "Traditional cafe near Mahane Yehuda", 31.7767, 35.2345),
            CoffeeShop("Haifa Bay Cafe", 4.4f, "Scenic coffee shop with bay views", 32.7940, 34.9896),
            CoffeeShop("Desert Bean", 4.2f, "Cozy spot in Beer Sheva's Old City", 31.2516, 34.7913),
            CoffeeShop("Marina Coffee", 4.6f, "Luxurious cafe by the Herzliya Marina", 32.1877, 34.8702),
            CoffeeShop("Sarona Coffee Works", 4.7f, "Trendy cafe in Sarona Market", 32.0731, 34.7925)
        )

        val adapter = FavoriteCoffeeAdapter(favoriteCoffeeShops) { coffeeShop ->
            // Handle click on coffee shop
            val bundle = Bundle().apply {
                putString("name", coffeeShop.name)
                putString("description", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.")
                putFloat("latitude", coffeeShop.latitude.toFloat())
                putFloat("longitude", coffeeShop.longitude.toFloat())
            }
            findNavController().navigate(R.id.action_favoriteFragment_to_coffeeFragment, bundle)
        }
        recyclerView.adapter = adapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.toolbarTitle).setText(R.string.favorites)
    }
}