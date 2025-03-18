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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private val repository = CoffeeShopRepository.getInstance()
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
                repository.getAllCoffeeShops().collectLatest { allCoffeeShops ->
                    val favoriteCoffeeShops = allCoffeeShops.filter { shop -> 
                        shop.placeId?.let { id -> favoriteIds.contains(id) } ?: false
                    }
                    
                    withContext(Dispatchers.Main) {
                        adapter = CoffeeShopAdapter(favoriteCoffeeShops, showCaptions = false)
                        recyclerView.adapter = adapter
                    }
                }
            } catch (e: Exception) {
                Log.e("FavoriteFragment", "Error loading favorite coffee shops", e)
            }
        }
    }
}