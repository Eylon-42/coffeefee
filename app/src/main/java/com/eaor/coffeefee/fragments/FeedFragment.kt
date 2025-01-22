package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
import com.google.android.material.floatingactionbutton.FloatingActionButton

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

        // Hide back button as this is the main feed screen
        view.findViewById<ImageButton>(R.id.backButton).visibility = View.GONE

        // Set up RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Sample data
        val feedItems = listOf(
            FeedItem(
                id = "1",
                userName = "User 1",
                userDescription = "@${CoffeeShop("Cafe Dizengoff", 4.5f, "Modern cafe in the heart of Tel Aviv", 32.0853, 34.7818).name}",
                coffeeShop = CoffeeShop("Cafe Dizengoff", 4.5f, "Modern cafe in the heart of Tel Aviv", 32.0853, 34.7818),
                reviewText = "Amazing atmosphere and great coffee! Must visit when in Tel Aviv."
            ),
            FeedItem(
                id = "2",
                userName = "User 2",
                userDescription = "@${CoffeeShop("Jerusalem Coffee House", 4.3f, "Traditional cafe near Mahane Yehuda", 31.7767, 35.2345).name}",
                coffeeShop = CoffeeShop("Jerusalem Coffee House", 4.3f, "Traditional cafe near Mahane Yehuda", 31.7767, 35.2345),
                reviewText = "Best traditional coffee in Jerusalem!"
            ),
            FeedItem(
                id = "3",
                userName = "User 3",
                userDescription = "Professional Coffee Taster",
                coffeeShop = CoffeeShop("Haifa Bay Cafe", 4.4f, "Scenic coffee shop with bay views", 32.7940, 34.9896),
                reviewText = "The view combined with their specialty coffee makes this place special."
            )
        )

        val adapter = FeedAdapter(
            feedItems,
            onMoreInfoClick = { feedItem ->
                // Navigate to CoffeeFragment with the coffee shop details
                val bundle = Bundle().apply {
                    putString("name", feedItem.coffeeShop.name)
                    putString("description", feedItem.coffeeShop.caption)
                    putFloat("latitude", feedItem.coffeeShop.latitude.toFloat())
                    putFloat("longitude", feedItem.coffeeShop.longitude.toFloat())
                }
                findNavController().navigate(R.id.action_feedFragment_to_coffeeFragment, bundle)
            },
            onCommentClick = { feedItem -> // Handle comment click
                val bundle = Bundle().apply {
                    putString("postId", feedItem.id)
                }
                findNavController().navigate(R.id.action_feedFragment_to_commentsFragment, bundle)
            },
            showOptionsMenu = false
        )
        recyclerView.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.addPostFab).setOnClickListener {
            findNavController().navigate(R.id.action_feedFragment_to_addPostFragment)
        }
    }
}