package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.util.Log
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
import com.eaor.coffeefee.MainActivity
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.CoffeeShopAdapter
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FavoriteFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CoffeeShopAdapter
    private lateinit var repository: CoffeeShopRepository
    private val scope = CoroutineScope(Dispatchers.Main)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_favorite, container, false)
        
        // Setup toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Favorites"
        
        repository = CoffeeShopRepository.getInstance(requireContext())
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = CoffeeShopAdapter(emptyList(), showCaptions = false)
        recyclerView.adapter = adapter
        
        loadFavoriteCoffeeShops()
        
        return view
    }

    private fun loadFavoriteCoffeeShops() {
        val userId = auth.currentUser?.uid ?: return
        
        scope.launch {
            try {
                // Get favorite coffee shop IDs
                val favoritesSnapshot = db.collection("users")
                    .document(userId)
                    .collection("favorites")
                    .get()
                    .await()
                
                val favoriteIds = favoritesSnapshot.documents.map { doc -> doc.id }.toSet()
                
                // Get all coffee shops and filter by favorites
                val allCoffeeShops = repository.getAllCoffeeShops()
                val favoriteCoffeeShops = allCoffeeShops.filter { shop -> 
                    shop.placeId?.let { id -> favoriteIds.contains(id) } ?: false
                }
                
                withContext(Dispatchers.Main) {
                    adapter = CoffeeShopAdapter(favoriteCoffeeShops, showCaptions = false)
                    setupAdapterClickListener(adapter)
                    recyclerView.adapter = adapter
                }
            } catch (e: Exception) {
                Log.e("FavoriteFragment", "Error loading favorite coffee shops", e)
            }
        }
    }
    
    private fun setupAdapterClickListener(adapter: CoffeeShopAdapter) {
        adapter.setOnItemClickListener { coffeeShop ->
            val bundle = Bundle().apply {
                putString("name", coffeeShop.name)
                putString("description", coffeeShop.description)
                putFloat("latitude", coffeeShop.latitude.toFloat())
                putFloat("longitude", coffeeShop.longitude.toFloat())
                putString("placeId", coffeeShop.placeId)
                coffeeShop.photoUrl?.let { putString("photoUrl", it) }
                coffeeShop.rating?.let { putFloat("rating", it) }
                coffeeShop.address?.let { putString("address", it) }
                
                // Add source fragment ID
                putInt("source_fragment_id", R.id.favoriteFragment)
            }
            findNavController().navigate(R.id.action_favoriteFragment_to_coffeeFragment, bundle)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Navigation component handles back stack management automatically
    }
}