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
            CoffeeShop("The Coffee Name", 5f, "Description"),
            CoffeeShop("The Coffee Name", 5f, "Description"),
            CoffeeShop("The Coffee Name", 5f, "Description"),
            CoffeeShop("The Coffee Name", 5f, "Description")
        )

        val adapter = FavoriteCoffeeAdapter(favoriteCoffeeShops) { coffeeShop ->
            // Handle click on coffee shop
            val bundle = Bundle().apply {
                putString("name", coffeeShop.name)
                putString("description", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.")
            }
            findNavController().navigate(R.id.action_favoriteFragment_to_coffeeFragment, bundle)
        }
        recyclerView.adapter = adapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Favorites"
    }
}