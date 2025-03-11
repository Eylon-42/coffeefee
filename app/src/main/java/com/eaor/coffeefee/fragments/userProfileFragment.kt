package com.eaor.coffeefee.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
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
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.models.FeedItem
import com.eaor.coffeefee.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.eaor.coffeefee.data.User
import com.squareup.picasso.Picasso

class UserProfileFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userRepository: UserRepository


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
        val userDao = AppDatabase.getDatabase(requireContext()).userDao()
        userRepository = UserRepository(userDao, db)

        // Setup toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Profile"

        // Hide back button in toolbar for main profile page
        view.findViewById<ImageButton>(R.id.backButton).visibility = View.GONE

        // Get current user and load their info
        val currentUser = auth.currentUser
        loadUserData(view)
        
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

        // Fetch user data first, then fetch posts
        if (currentUserId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Get user data
                    val userData = userRepository.getUserData(currentUserId)
                    
                    // Now fetch posts
                    fetchUserPosts(currentUserId, userData, view, recyclerView)
                } catch (e: Exception) {
                    Log.e("UserProfileFragment", "Error getting user data: ${e.message}")
                    Toast.makeText(
                        context,
                        "Error loading user data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Fetch posts even if user data fails
                    fetchUserPosts(currentUserId, null, view, recyclerView)
                }
            }
        }
    }
    
    private fun fetchUserPosts(userId: String, userData: User?, view: View, recyclerView: RecyclerView) {
        db.collection("Posts")
            .whereEqualTo("UserId", userId) // Filter by current user's ID
            .get()
            .addOnSuccessListener { result ->
                val userPosts = mutableListOf<FeedItem>()
                for (document in result) { // Set the document ID to FeedItem

                    val locationMap = document.get("location") as? Map<String, Any>
                    val location = if (locationMap != null) {
                        FeedItem.Location(
                            name = locationMap["name"] as? String ?: "",
                            latitude = (locationMap["latitude"] as? Double) ?: 0.0,
                            longitude = (locationMap["longitude"] as? Double) ?: 0.0
                        )
                    } else {
                        null // If location is not available, we set it to null
                    }

                    var tepItem = FeedItem(
                        id = document.id,
                        userId = document.getString("UserId") ?: "",
                        userName = "You", // Placeholder for now, we'll update later
                        experienceDescription = document.getString("experienceDescription") ?: "",
                        location = location,
                        photoUrl = document.getString("photoUrl"),
                        timestamp = document.getLong("timestamp") ?: 0L,
                        userPhotoUrl = null // Initialize as null to avoid Picasso errors
                    )

                    // Fetch the download URL from Firebase Storage for the user's profile photo
                    if (!userData?.profilePictureUrl.isNullOrEmpty()) {
                        try {
                            val storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(userData!!.profilePictureUrl!!)
                            storageReference.downloadUrl.addOnSuccessListener { uri ->
                                // Convert gs:// URL to https:// URL
                                tepItem.userPhotoUrl = uri.toString()
                            }.addOnFailureListener { e ->
                                Log.e("UserProfileFragment", "Error getting download URL: ${e.message}")
                            }
                        } catch (e: Exception) {
                            Log.e("UserProfileFragment", "Error with storage reference: ${e.message}")
                        }
                    }
                    userPosts.add(tepItem) // Add to the list of posts
                }

                // Check if userPosts has any data, otherwise show a message
                if (userPosts.isEmpty()) {
                    // Show a message like "No posts available" here, or an empty view
                    view.findViewById<TextView>(R.id.noPostsMessage).visibility = View.VISIBLE
                } else {
                    // Hide the "No posts available" message if posts are present
                    view.findViewById<TextView>(R.id.noPostsMessage).visibility = View.GONE
                }

                // Set up the adapter with the filtered posts
                val adapter = FeedAdapter(
                    userPosts,
                    onMoreInfoClick = { feedItem ->
                        val bundle = Bundle().apply {
                            putString("description", feedItem.experienceDescription ?: "Unknown Location")
                            putString("name", feedItem.location?.name ?: "Unknown Location")
                            putFloat("latitude", feedItem.location?.latitude?.toFloat() ?: 0f)
                            putFloat("longitude", feedItem.location?.longitude?.toFloat() ?: 0f)
                            putString("imageUrl", feedItem.photoUrl) // Pass the photo URL here
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
                                        // Delete the post from Firestore
                                        val postId = userPosts[position].id
                                        db.collection("Posts").document(postId)
                                            .delete()
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "Post deleted successfully", Toast.LENGTH_SHORT).show()
                                                userPosts.removeAt(position) // Remove the post from the list
                                                notifyItemRemoved(position) // Update the RecyclerView
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(context, "Error deleting post: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        true
                                    }
                                    else -> false
                                }
                            }
                            show()
                        }
                    }
                }

                // Set the adapter only after posts are fetched
                recyclerView.adapter = adapter
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error getting posts: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserData(view: View) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val user = userRepository.getUserData(currentUser.uid)
                    user?.let {
                        view.findViewById<TextView>(R.id.userName).text = it.name

                        // Set a default profile image first
                        view.findViewById<ImageView>(R.id.userAvatar).setImageResource(R.drawable.ic_profile)
                        
                        // Only try to load the profile picture if the URL is not null or empty
                        if (!it.profilePictureUrl.isNullOrEmpty()) {
                            Picasso.get()
                                .load(it.profilePictureUrl)
                                .placeholder(R.drawable.ic_profile) // Placeholder image
                                .error(R.drawable.ic_profile) // Error image (without casting)
                                .into(view.findViewById<ImageView>(R.id.userAvatar))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UserRepository", "Error loading user data: ${e.message}")
                    Toast.makeText(
                        context,
                        "Error loading user data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Set a default profile image in case of error
                    view.findViewById<ImageView>(R.id.userAvatar).setImageResource(R.drawable.ic_profile)
                }
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
            R.id.action_edit_profile -> {
                // Navigate to ProfileFragment
                findNavController().navigate(R.id.action_userProfileFragment_to_profileFragment)
                true
            }
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
