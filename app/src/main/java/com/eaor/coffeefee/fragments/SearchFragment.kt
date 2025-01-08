package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.CoffeeShopAdapter
import com.eaor.coffeefee.models.CoffeeShop

class SearchFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_search, container, false)

        // Initialize Toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        // Set up toolbar with AppCompatActivity
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        // Hide the title since we're using a custom title in the toolbar XML
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // Set up navigation button click listener
        view.findViewById<ImageButton>(R.id.navigationButton).setOnClickListener {
            findNavController().navigate(R.id.action_searchFragment_to_coffeeMapFragment)
        }

        // Initialize RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize CoffeeShopAdapter
        val coffeeShops = listOf(
            CoffeeShop("First Coffee Shop", 3f, "Caption"),
            CoffeeShop("Second Coffee Shop", 3f, "Caption"),
            CoffeeShop("Third Coffee Shop", 3f, "Caption"),
            CoffeeShop("Fourth Coffee Shop", 3f, "Caption"),
            CoffeeShop("Fifth Coffee Shop", 3f, "Caption"),
            CoffeeShop("Sixth Coffee Shop", 3f, "Caption")
        )
        val adapter = CoffeeShopAdapter(coffeeShops)
        recyclerView.adapter = adapter

        // Initialize SearchView
        val searchView = view.findViewById<SearchView>(R.id.searchView)
        setupSearchView(searchView, adapter)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Search"
        
        // Show and set up navigation button
        view.findViewById<ImageButton>(R.id.navigationButton).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                findNavController().navigate(R.id.action_searchFragment_to_coffeeMapFragment)
            }
        }
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