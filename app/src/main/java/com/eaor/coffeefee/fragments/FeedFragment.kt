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
import com.eaor.coffeefee.adapters.FeedAdapter
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.models.FeedItem

class FeedFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Feed"

        // Set up RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Sample data
        val feedItems = listOf(
            FeedItem(
                "User 1",
                "Coffee Enthusiast",
                CoffeeShop("Cafe Dizengoff", 4.5f, "Modern cafe in the heart of Tel Aviv", 32.0853, 34.7818),
                "Amazing atmosphere and great coffee! Must visit when in Tel Aviv."
            ),
            FeedItem(
                "User 2",
                "Coffee Connoisseur",
                CoffeeShop("Jerusalem Coffee House", 4.3f, "Traditional cafe near Mahane Yehuda", 31.7767, 35.2345),
                "Traditional coffee making at its finest. The aroma is incredible!"
            ),
            FeedItem(
                "User 3",
                "Professional Coffee Taster",
                CoffeeShop("Haifa Bay Cafe", 4.4f, "Scenic coffee shop with bay views", 32.7940, 34.9896),
                "The view combined with their specialty coffee makes this place special."
            )
        )

        val adapter = FeedAdapter(feedItems) { feedItem ->
            // Navigate to CoffeeFragment with the coffee shop details
            val bundle = Bundle().apply {
                putString("name", feedItem.coffeeShop.name)
                putString("description", feedItem.coffeeShop.caption)
                putFloat("latitude", feedItem.coffeeShop.latitude.toFloat())
                putFloat("longitude", feedItem.coffeeShop.longitude.toFloat())
            }
            findNavController().navigate(R.id.action_feedFragment_to_coffeeFragment, bundle)
        }
        recyclerView.adapter = adapter
    }
}