package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.CoffeeShopAdapter
import com.eaor.coffeefee.viewmodels.SuggestionViewModel

class SuggestionFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CoffeeShopAdapter
    private lateinit var noSuggestionsMessage: TextView
    private lateinit var loadingContainer: View
    private lateinit var viewModel: SuggestionViewModel

    override fun onResume() {
        super.onResume()
        // Force refresh to get the latest suggestions
        viewModel.loadUserTags(forceRefresh = true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_suggestion, container, false)
        
        // Setup toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "AI Suggestions For You"
        
        recyclerView = view.findViewById(R.id.recyclerView)
        noSuggestionsMessage = view.findViewById(R.id.noSuggestionsMessage)
        loadingContainer = view.findViewById(R.id.loadingContainer)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Initialize the adapter with empty list
        adapter = CoffeeShopAdapter(emptyList(), showCaptions = true)
        recyclerView.adapter = adapter
        
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Hide back button
        view.findViewById<ImageButton>(R.id.backButton).visibility = View.GONE
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(SuggestionViewModel::class.java)
        viewModel.initializeRepository(requireContext())
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Observe error state
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null && error.isNotEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }
        
        // Observe user tags
        viewModel.userTags.observe(viewLifecycleOwner) { tags ->
            if (tags.isNullOrEmpty()) {
                noSuggestionsMessage.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                noSuggestionsMessage.text = "No preferences found. Please update your profile to get recommendations."
            } else {
                Log.d("SuggestionFragment", "User tags loaded: $tags")
                // Let the loadUserTags handle generating suggestions
            }
        }
        
        // Observe suggested coffee shops
        viewModel.suggestedCoffeeShops.observe(viewLifecycleOwner) { suggestions ->
            if (suggestions.isNullOrEmpty()) {
                noSuggestionsMessage.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                noSuggestionsMessage.text = "No coffee shops match your preferences."
            } else {
                noSuggestionsMessage.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                
                Log.d("SuggestionFragment", "Loaded ${suggestions.size} suggestions")
                
                // Create a map of coffee shop placeId to reasons
                val reasonsMap = suggestions.associate { 
                    it.first.placeId to it.second 
                }
                
                // Update the adapter with the suggested coffee shops
                adapter = CoffeeShopAdapter(
                    suggestions.map { it.first },
                    showCaptions = true,
                    viewType = CoffeeShopAdapter.VIEW_TYPE_SUGGESTION,
                    matchingReasonsMap = reasonsMap
                )
                
                // Set click listener for coffee shops
                adapter.setOnItemClickListener { coffeeShop ->
                    val bundle = Bundle().apply {
                        putString("name", coffeeShop.name)
                        putString("description", coffeeShop.description)
                        putFloat("latitude", coffeeShop.latitude.toFloat())
                        putFloat("longitude", coffeeShop.longitude.toFloat())
                        putString("placeId", coffeeShop.placeId)
                        putString("photoUrl", coffeeShop.photoUrl)
                        putString("address", coffeeShop.address)
                        coffeeShop.rating?.let { rating ->
                            if (rating > 0f) {
                                putFloat("rating", rating)
                            }
                        }
                    }
                    findNavController().navigate(R.id.action_suggestionFragment_to_coffeeFragment, bundle)
                }
                
                recyclerView.adapter = adapter
            }
        }
        
        // Initialize suggestions
        viewModel.loadUserTags()
    }
}