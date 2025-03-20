package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.CoffeeShopAdapter
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import com.eaor.coffeefee.utils.VertexAIService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SuggestionFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CoffeeShopAdapter
    private lateinit var noSuggestionsMessage: TextView
    private val repository = CoffeeShopRepository.getInstance()
    private val vertexAIService = VertexAIService.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

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
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = CoffeeShopAdapter(emptyList(), showCaptions = true)
        recyclerView.adapter = adapter
        
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadUserTagsAndSuggestCoffeeShops()
    }
    
    private fun loadUserTagsAndSuggestCoffeeShops() {
        val userId = auth.currentUser?.uid ?: return
        
        scope.launch {
            try {
                // Show loading state
                withContext(Dispatchers.Main) {
                    recyclerView.visibility = View.GONE
                    noSuggestionsMessage.visibility = View.GONE
                    // TODO: Add a progress indicator
                }
                
                val userSnapshot = db.collection("Users")
                    .document(userId)
                    .get()
                    .await()

                Log.d("SuggestionFragment", "User tags snapshot: $userSnapshot")

                val userTags = userSnapshot.get("tags") as? List<String> ?: emptyList()
                
                if (userTags.isEmpty()) {
                    Log.d("SuggestionFragment", "No user tags found")
                    // Show the "no suggestions" message
                    withContext(Dispatchers.Main) {
                        noSuggestionsMessage.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }
                    return@launch
                }
                
                Log.d("SuggestionFragment", "User tags: $userTags")
                
                // Get all coffee shops safely
                try {
                    // Use collect instead of first to handle flow more gracefully
                    val coffeeShops = mutableListOf<CoffeeShop>()
                    repository.getAllCoffeeShops().collect { shops ->
                        coffeeShops.addAll(shops)
                    }
                    
                    Log.d("SuggestionFragment", "Successfully collected ${coffeeShops.size} coffee shops")
                    
                    if (coffeeShops.isEmpty()) {
                        Log.d("SuggestionFragment", "No coffee shops available")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "No coffee shops available", Toast.LENGTH_SHORT).show()
                            noSuggestionsMessage.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        }
                        return@launch
                    }
                    
                    // Use VertexAI to generate suggestions
                    generateAISuggestions(userTags, coffeeShops)
                    
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.d("SuggestionFragment", "Coffee shop collection was cancelled")
                        // Don't update UI if cancelled, as the fragment might be gone
                        return@launch
                    }
                    
                    Log.e("SuggestionFragment", "Error getting coffee shops", e)
                    withContext(Dispatchers.Main) {
                        if (isAdded) { // Check if fragment is still attached
                            Toast.makeText(requireContext(), "Error loading coffee shops", Toast.LENGTH_SHORT).show()
                            noSuggestionsMessage.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e("SuggestionFragment", "Error loading user tags", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error loading suggestions", Toast.LENGTH_SHORT).show()
                    noSuggestionsMessage.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }
            }
        }
    }

    private suspend fun generateAISuggestions(userTags: List<String>, allCoffeeShops: List<CoffeeShop>) {
        try {
            // Call VertexAI service to get suggestions
            val result = vertexAIService.generateCoffeeShopSuggestions(userTags, allCoffeeShops)
            
            result.fold(
                onSuccess = { suggestions ->
                    withContext(Dispatchers.Main) {
                        if (suggestions.isEmpty()) {
                            // Show the "no suggestions" message
                            noSuggestionsMessage.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                            
                            Log.d("SuggestionFragment", "No AI suggestions found")
                        } else {
                            // Hide the message and show the recyclerView
                            noSuggestionsMessage.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            
                            Log.d("SuggestionFragment", "Found ${suggestions.size} AI suggested coffee shops")
                            
                            // Create a map of coffee shop placeId to reasons
                            val reasonsMap = suggestions.associate { 
                                it.first.placeId to it.second 
                            }
                            
                            // Update the adapter with the suggested coffee shops
                            adapter = CoffeeShopAdapter(
                                suggestions.map { it.first },
                                showCaptions = true,
                                matchingTagsMap = reasonsMap
                            )
                            
                            // Set click listener for coffee shops
                            adapter.setOnItemClickListener { coffeeShop ->
                                val bundle = Bundle().apply {
                                    putString("name", coffeeShop.name)
                                    putString("description", coffeeShop.caption ?: "")
                                }
                                findNavController().navigate(R.id.action_suggestionFragment_to_coffeeFragment, bundle)
                            }
                            
                            recyclerView.adapter = adapter
                        }
                    }
                },
                onFailure = { error ->
                    Log.e("SuggestionFragment", "Error getting AI suggestions", error)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error getting AI suggestions", Toast.LENGTH_SHORT).show()
                        
                        // Fall back to regular tag matching
                        loadSuggestedCoffeeShops(userTags, allCoffeeShops)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("SuggestionFragment", "Exception during AI suggestion generation", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Error generating AI suggestions", Toast.LENGTH_SHORT).show()
                
                // Fall back to regular tag matching
                loadSuggestedCoffeeShops(userTags, allCoffeeShops)
            }
        }
    }

    private fun loadSuggestedCoffeeShops(userTags: List<String>, allCoffeeShops: List<CoffeeShop>) {
        if (userTags.isEmpty()) {
            Log.d("SuggestionFragment", "No user tags found")
            noSuggestionsMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }
        
        // Find coffee shops with matching tags using the basic approach
        val suggestedCoffeeShops = allCoffeeShops.map { shop ->
            // Get shop tags
            val shopTags = shop.tags ?: emptyList()
            
            // Find matching tags
            val matchingTags = shopTags.filter { shopTag ->
                userTags.contains(shopTag)
            }
            
            // Create pair of coffee shop and its matching tags
            Pair(shop, matchingTags)
        }
        // Filter out shops with no matches and sort by match score
        .filter { (_, matchingTags) -> matchingTags.isNotEmpty() }
        .sortedByDescending { (_, matchingTags) -> matchingTags.size }
        
        if (suggestedCoffeeShops.isEmpty()) {
            // Show the "no suggestions" message
            noSuggestionsMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            
            Log.d("SuggestionFragment", "No matching coffee shops found")
        } else {
            // Hide the message and show the recyclerView
            noSuggestionsMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            
            Log.d("SuggestionFragment", "Found ${suggestedCoffeeShops.size} matching coffee shops")
            
            adapter = CoffeeShopAdapter(
                suggestedCoffeeShops.map { it.first },
                showCaptions = true,
                matchingTagsMap = suggestedCoffeeShops.associate { 
                    it.first.placeId to it.second 
                }
            )
            
            // Set click listener for coffee shops
            adapter.setOnItemClickListener { coffeeShop ->
                val bundle = Bundle().apply {
                    putString("name", coffeeShop.name)
                    putString("description", coffeeShop.caption ?: "")
                }
                findNavController().navigate(R.id.action_suggestionFragment_to_coffeeFragment, bundle)
            }
            
            recyclerView.adapter = adapter
        }
    }
} 