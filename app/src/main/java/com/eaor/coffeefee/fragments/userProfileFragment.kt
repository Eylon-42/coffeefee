package com.eaor.coffeefee.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.AuthActivity
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.FeedAdapter
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

        // Get current user and load their info
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Load user's name from Firestore
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

        // Set user description
        view.findViewById<TextView>(R.id.userAbout).text =
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor."

        // Setup edit button with dropdown menu
        val editButton = view.findViewById<ImageButton>(R.id.editButton)
        editButton.setOnClickListener {
            showPopupMenu(it)
        }

        // Set up RecyclerView for posts
        val recyclerView = view.findViewById<RecyclerView>(R.id.postsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Get current user's ID
        val currentUserId = currentUser?.uid

        // Fetch posts for the current user from Firestore
        if (currentUserId != null) {
            db.collection("Posts")
                .whereEqualTo("userId", currentUserId) // Filter by current user's ID
                .get()
                .addOnSuccessListener { result ->
                    val userPosts = mutableListOf<FeedItem>()
                    for (document in result) {
                        val feedItem = document.toObject(FeedItem::class.java)
                        userPosts.add(feedItem) // Add to the list of posts
                    }

                    // Set up the adapter with the filtered posts
                    val adapter = FeedAdapter(
                        userPosts,
                        onMoreInfoClick = { feedItem ->
                            val bundle = Bundle().apply {
                                putString("name", feedItem.location.name)
                                putString("description", feedItem.experienceDescription)
                                putDouble("latitude", feedItem.location.latitude)
                                putDouble("longitude", feedItem.location.longitude)
                            }
                            findNavController().navigate(R.id.action_userProfileFragment_to_coffeeFragment, bundle)
                        },
                        onCommentClick = { feedItem ->
                            val bundle = Bundle().apply {
                                putString("postId", feedItem.id)
                            }
                            findNavController().navigate(R.id.action_userProfileFragment_to_commentsFragment, bundle)
                        },
                        showOptionsMenu = true
                    ).apply {
                        setPostOptionsClickListener { view, position ->
                            PopupMenu(requireContext(), view, R.style.PopupMenuStyle).apply {
                                menuInflater.inflate(R.menu.post_options_menu, menu)
                                setOnMenuItemClickListener { item ->
                                    when (item.itemId) {
                                        R.id.action_edit_post -> {
                                            val bundle = Bundle().apply {
                                                putString("postText", userPosts[position].experienceDescription)
                                            }
                                            findNavController().navigate(R.id.action_userProfileFragment_to_editPostFragment, bundle)
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
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "Error getting posts: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showPopupMenu(view: View) {
        val popupMenu = PopupMenu(requireContext(), view)
        val inflater: MenuInflater = popupMenu.menuInflater
        inflater.inflate(R.menu.profile_edit_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            handleMenuItemClick(menuItem)
        }
        popupMenu.show()
    }

    private fun handleMenuItemClick(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_logout -> {
                logoutUser()
                true
            }
            else -> false
        }
    }

    private fun logoutUser() {
        auth.signOut()
        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()

        // Navigate to LoginActivity
        val intent = Intent(requireContext(), AuthActivity::class.java)
        startActivity(intent)
        requireActivity().finish() // Optional: Finish the current activity if you want to remove it from the back stack
    }
}
