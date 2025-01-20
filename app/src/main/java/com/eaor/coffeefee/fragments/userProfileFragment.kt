package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.Toolbar
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.FeedAdapter
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.models.FeedItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserProfileFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        // Setup toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Profile"
        
        // Hide back button in toolbar for main profile page
        view.findViewById<ImageButton>(R.id.backButton).visibility = View.GONE

        // Load user name from Firestore
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("Users")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("name")
                        if (name != null) {
                            view.findViewById<TextView>(R.id.userName).text = name
                        }
                    }
                }
        }

        // Set user info
        view.findViewById<TextView>(R.id.userAbout).text = 
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor."

        // Setup edit button with dropdown menu
        val editButton = view.findViewById<ImageButton>(R.id.editButton)
        editButton.setOnClickListener { v ->
            PopupMenu(requireContext(), v).apply {
                menuInflater.inflate(R.menu.profile_edit_menu, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit_profile -> {
                            findNavController().navigate(R.id.action_userProfileFragment_to_profileFragment)
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        // Setup RecyclerView for posts
        val recyclerView = view.findViewById<RecyclerView>(R.id.postsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Sample posts data
        val userPosts = listOf(
            FeedItem(
                "Current User",
                "@${CoffeeShop("Cafe Dizengoff", 4.5f, "Modern cafe in Tel Aviv", 32.0853, 34.7818).name}",
                CoffeeShop("Cafe Dizengoff", 4.5f, "Modern cafe in Tel Aviv", 32.0853, 34.7818),
                "Amazing atmosphere and great coffee!"
            ),
            FeedItem(
                "Current User",
                "@${CoffeeShop("Jerusalem Coffee House", 4.3f, "Traditional cafe", 31.7767, 35.2345).name}",
                CoffeeShop("Jerusalem Coffee House", 4.3f, "Traditional cafe", 31.7767, 35.2345),
                "Best traditional coffee in Jerusalem!"
            )
        )

        val adapter = FeedAdapter(userPosts, 
            onMoreInfoClick = { feedItem ->
                val bundle = Bundle().apply {
                    putString("name", feedItem.coffeeShop.name)
                    putString("description", feedItem.coffeeShop.caption)
                    putFloat("latitude", feedItem.coffeeShop.latitude.toFloat())
                    putFloat("longitude", feedItem.coffeeShop.longitude.toFloat())
                }
                findNavController().navigate(R.id.action_userProfileFragment_to_coffeeFragment, bundle)
            },
            showOptionsMenu = true
        ).apply {
            setPostOptionsClickListener { view, position ->
                PopupMenu(requireContext(), view).apply {
                    menuInflater.inflate(R.menu.post_options_menu, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_edit_post -> {
                                // TODO: Implement edit functionality
                                true
                            }
                            R.id.action_delete_post -> {
                                // TODO: Implement delete functionality
                                true
                            }
                            else -> false
                        }
                    }
                    show()
                }
            }
        }
        recyclerView.adapter = adapter
    }
}