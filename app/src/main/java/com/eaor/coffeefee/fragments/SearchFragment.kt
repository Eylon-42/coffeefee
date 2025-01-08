package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.CoffeeShopAdapter

class SearchFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_search, container, false)

        // Initialize Toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            // Navigate to the CoffeeMapFragment
            findNavController().navigate(R.id.coffeeMapFragment)
        }

        // Initialize RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize CoffeeShopAdapter
        val coffeeShops = listOf(
            "First Coffee Shop", "Second Coffee Shop", "Third Coffee Shop",
            "Fourth Coffee Shop", "Fifth Coffee Shop", "Sixth Coffee Shop"
        )
        val adapter = CoffeeShopAdapter(coffeeShops)
        recyclerView.adapter = adapter

        // Initialize SearchView
        val searchView = view.findViewById<SearchView>(R.id.searchView)
        setupSearchView(searchView, adapter)

        return view
    }

    private fun setupSearchView(searchView: SearchView, adapter: CoffeeShopAdapter) {
        // Set up filtering for RecyclerView through SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false // We handle filtering in onQueryTextChange
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Filter adapter items based on query
                adapter.filter(newText.orEmpty())
                return true
            }
        })
    }
}