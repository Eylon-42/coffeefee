package com.eaor.coffeefee.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
import com.google.android.material.bottomnavigation.BottomNavigationView

class SearchFragment : Fragment() {
    private lateinit var bottomNav: BottomNavigationView
    
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
            CoffeeShop("Cafe Dizengoff", 4.5f, "Modern cafe in the heart of Tel Aviv", 32.0853, 34.7818),
            CoffeeShop("Jerusalem Coffee House", 4.3f, "Traditional cafe near Mahane Yehuda", 31.7767, 35.2345),
            CoffeeShop("Haifa Bay Cafe", 4.4f, "Scenic coffee shop with bay views", 32.7940, 34.9896),
            CoffeeShop("Desert Bean", 4.2f, "Cozy spot in Beer Sheva's Old City", 31.2516, 34.7913),
            CoffeeShop("Marina Coffee", 4.6f, "Luxurious cafe by the Herzliya Marina", 32.1877, 34.8702),
            CoffeeShop("Sarona Coffee Works", 4.7f, "Trendy cafe in Sarona Market", 32.0731, 34.7925)
        )
        val adapter = CoffeeShopAdapter(coffeeShops)
        recyclerView.adapter = adapter

        // Initialize SearchView
        val searchView = view.findViewById<SearchView>(R.id.searchView)
        setupSearchView(searchView, adapter)

        adapter.setOnItemClickListener { coffeeShop ->
            val bundle = Bundle().apply {
                putString("name", coffeeShop.name)
                putString("description", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor.")
                putFloat("latitude", coffeeShop.latitude.toFloat())
                putFloat("longitude", coffeeShop.longitude.toFloat())
            }
            findNavController().navigate(R.id.action_searchFragment_to_coffeeFragment, bundle)
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bottomNav = requireActivity().findViewById(R.id.bottom_nav)
        
        // Initialize SearchView
        val searchView = view.findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            bottomNav.visibility = if (hasFocus) View.GONE else View.VISIBLE
        }
        
        // Set up the RecyclerView to adjust its padding when keyboard shows
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.addOnLayoutChangeListener { v, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                recyclerView.postDelayed({
                    recyclerView.smoothScrollToPosition(0)
                }, 100)
            }
        }
        val toolbarView = view.findViewById<View>(R.id.toolbar)
        // Get the included toolbar view
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbarView.findViewById<TextView>(R.id.toolbarTitle).text = "Search"
        // Hide back button as this is the main search screen
        view.findViewById<ImageButton>(R.id.backButton).visibility = View.GONE

        // Show and set up navigation button
        toolbar.findViewById<ImageButton>(R.id.navigationButton).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                findNavController().navigate(R.id.action_searchFragment_to_coffeeMapFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure bottom nav is visible when leaving the fragment
        bottomNav.visibility = View.VISIBLE
    }

    private fun setupSearchView(searchView: SearchView, adapter: CoffeeShopAdapter) {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Hide keyboard on submit
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchView.windowToken, 0)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText.orEmpty())
                return true
            }
        })
    }
}