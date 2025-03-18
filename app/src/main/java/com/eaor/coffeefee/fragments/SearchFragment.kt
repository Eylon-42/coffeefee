package com.eaor.coffeefee.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
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
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var adapter: CoffeeShopAdapter
    private val repository = CoffeeShopRepository.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main)
    
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

        // Initialize CoffeeShopAdapter with empty list
        adapter = CoffeeShopAdapter(emptyList())
        recyclerView.adapter = adapter

        // Load initial data
        loadCoffeeShops()

        // Set up click listener
        adapter.setOnItemClickListener { coffeeShop ->
            val bundle = Bundle().apply {
                putString("name", coffeeShop.name)
                putString("description", coffeeShop.caption)
                putFloat("latitude", coffeeShop.latitude.toFloat())
                putFloat("longitude", coffeeShop.longitude.toFloat())
                putString("placeId", coffeeShop.placeId)
                coffeeShop.photoUrl?.let { putString("photoUrl", it) }
                coffeeShop.rating?.let { putFloat("rating", it) }
                coffeeShop.address?.let { putString("address", it) }
            }
            findNavController().navigate(R.id.action_searchFragment_to_coffeeFragment, bundle)
        }

        // Initialize SearchView
        val searchView = view.findViewById<SearchView>(R.id.searchView)
        setupSearchView(searchView)

        return view
    }

    private fun loadCoffeeShops() {
        scope.launch {
            repository.getAllCoffeeShops().collectLatest { coffeeShops ->
                withContext(Dispatchers.Main) {
                    if (this@SearchFragment::adapter.isInitialized) {
                        adapter.updateData(coffeeShops)
                    } else {
                        adapter = CoffeeShopAdapter(coffeeShops)
                        view?.findViewById<RecyclerView>(R.id.recyclerView)?.adapter = adapter
                        setupAdapterClickListener()
                    }
                }
            }
        }
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

    private fun setupSearchView(searchView: SearchView) {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchCoffeeShops(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { searchCoffeeShops(it) }
                return true
            }
        })
    }

    private fun searchCoffeeShops(query: String) {
        // Log the search attempt
        Log.d("SearchFragment", "Searching for: '$query'")
        
        scope.launch {
            try {
                val results = repository.searchCoffeeShops(query)
                
                // Log the results
                Log.d("SearchFragment", "Search returned ${results.size} results")
                
                withContext(Dispatchers.Main) {
                    if (this@SearchFragment::adapter.isInitialized) {
                        adapter.updateData(results)
                        Log.d("SearchFragment", "Updated adapter with ${results.size} results")
                    } else {
                        adapter = CoffeeShopAdapter(results)
                        view?.findViewById<RecyclerView>(R.id.recyclerView)?.adapter = adapter
                        setupAdapterClickListener()
                        Log.d("SearchFragment", "Created new adapter with ${results.size} results")
                    }
                    
                    // Add empty state TextView if it doesn't exist yet
                    val recyclerView = view?.findViewById<RecyclerView>(R.id.recyclerView)
                    var emptyView = view?.findViewById<TextView>(R.id.emptyStateText)
                    
                    if (emptyView == null && recyclerView != null) {
                        // Create the empty state TextView dynamically if it doesn't exist in layout
                        val parent = recyclerView.parent as? ViewGroup
                        if (parent != null) {
                            emptyView = TextView(requireContext()).apply {
                                id = View.generateViewId()
                                text = "No coffee shops found"
                                textSize = 16f
                                textAlignment = View.TEXT_ALIGNMENT_CENTER
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                setPadding(0, 100, 0, 0)
                            }
                            parent.addView(emptyView)
                        }
                    }
                    
                    // Show/hide empty state message
                    emptyView?.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                Log.e("SearchFragment", "Search error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // Show error message
                    Toast.makeText(context, "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupAdapterClickListener() {
        adapter.setOnItemClickListener { coffeeShop ->
            val bundle = Bundle().apply {
                putString("name", coffeeShop.name)
                putString("description", coffeeShop.caption)
                putFloat("latitude", coffeeShop.latitude.toFloat())
                putFloat("longitude", coffeeShop.longitude.toFloat())
                putString("placeId", coffeeShop.placeId)
                coffeeShop.photoUrl?.let { putString("photoUrl", it) }
                coffeeShop.rating?.let { putFloat("rating", it) }
                coffeeShop.address?.let { putString("address", it) }
            }
            findNavController().navigate(R.id.action_searchFragment_to_coffeeFragment, bundle)
        }
    }
}