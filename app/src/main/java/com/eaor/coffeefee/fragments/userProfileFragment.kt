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
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.QuerySnapshot
import com.eaor.coffeefee.models.CoffeeShop
import com.google.firebase.firestore.Query

class UserProfileFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userRepository: UserRepository
    private val repository = CoffeeShopRepository.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var recyclerView: RecyclerView
    private lateinit var feedAdapter: FeedAdapter
    private val userPosts = mutableListOf<FeedItem>()
    private lateinit var noPostsMessage: TextView

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
        
        // Initialize views
        noPostsMessage = view.findViewById(R.id.noPostsMessage)
        
        // Setup edit button with dropdown menu
        val editButton = view.findViewById<ImageButton>(R.id.editButton)
        editButton.setOnClickListener {
            showPopupMenu(it)
        }
        
        // Set up RecyclerView for posts
        recyclerView = view.findViewById(R.id.postsRecyclerView)
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
        // Check for posts with both "UserId" and "userId" to catch all posts
        val query1 = db.collection("Posts").whereEqualTo("UserId", userId)
        val query2 = db.collection("Posts").whereEqualTo("userId", userId)
        
        // Track if we have any results from either query
        var postsFound = false
        
        // First check with uppercase "UserId"
        query1.get()
            .addOnSuccessListener { result ->
                userPosts.clear()
                
                if (!result.isEmpty) {
                    postsFound = true
                    Log.d("UserProfileFragment", "Found ${result.size()} posts with UserId")
                    processPostResults(result, userData, view, recyclerView)
                } else {
                    // If no results with uppercase, try lowercase
                    query2.get()
                        .addOnSuccessListener { result2 ->
                            if (!result2.isEmpty) {
                                postsFound = true
                                Log.d("UserProfileFragment", "Found ${result2.size()} posts with userId")
                                processPostResults(result2, userData, view, recyclerView)
                            } else {
                                // No posts found with either case
                                Log.d("UserProfileFragment", "No posts found for user $userId")
                                view.findViewById<TextView>(R.id.noPostsMessage).visibility = View.VISIBLE
                            }
                        }
                        .addOnFailureListener { exception ->
                            Toast.makeText(context, "Error getting posts: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error getting posts: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // New helper function to process query results
    private fun processPostResults(result: QuerySnapshot, userData: User?, view: View, recyclerView: RecyclerView) {
        for (document in result) {
            val locationMap = document.get("location") as? Map<String, Any>
            val location = if (locationMap != null) {
                FeedItem.Location(
                    name = locationMap["name"] as? String ?: "",
                    latitude = (locationMap["latitude"] as? Double) ?: 0.0,
                    longitude = (locationMap["longitude"] as? Double) ?: 0.0,
                    placeId = locationMap["placeId"] as? String
                )
            } else {
                null // If location is not available, we set it to null
            }

            // Check for both UserId and userId to be safe
            val postUserId = document.getString("UserId") ?: document.getString("userId") ?: ""
            
            val tepItem = FeedItem(
                id = document.id,
                userId = postUserId,
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
            userPosts.add(tepItem)
        }
        
        // Update visibility of "no posts" message
        if (userPosts.isEmpty()) {
            noPostsMessage.visibility = View.VISIBLE
        } else {
            noPostsMessage.visibility = View.GONE
            
            // Initialize adapter with the posts
            feedAdapter = FeedAdapter(
                userPosts,
                onMoreInfoClick = { feedItem ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            // First try to get the coffee shop directly by placeId if available
                            val placeId = feedItem.location?.placeId
                            var coffeeShop: CoffeeShop? = null
                            
                            if (placeId != null) {
                                // Try to get shop directly by ID first (most reliable)
                                coffeeShop = repository.getCoffeeShop(placeId)
                            }
                            
                            // If not found by ID, try by name
                            if (coffeeShop == null) {
                                val coffeeShops = repository.getAllCoffeeShops().first()
                                coffeeShop = coffeeShops.find { shop -> 
                                    shop.name == feedItem.location?.name || shop.placeId == feedItem.location?.placeId
                                }
                            }
                            
                            // Make sure we log what caption we're using
                            if (coffeeShop != null) {
                                Log.d("UserProfileFragment", "Found coffee shop: ${coffeeShop.name}, caption: ${coffeeShop.caption}")
                            } else {
                                Log.d("UserProfileFragment", "Coffee shop not found for ${feedItem.location?.name}")
                            }

                            withContext(Dispatchers.Main) {
                                val bundle = Bundle().apply {
                                    putString("name", feedItem.location?.name ?: "Unknown Location")
                                    putString("description", coffeeShop?.caption ?: "No description available")
                                    putFloat("latitude", feedItem.location?.latitude?.toFloat() ?: 0f)
                                    putFloat("longitude", feedItem.location?.longitude?.toFloat() ?: 0f)
                                    putString("postId", feedItem.id)
                                    
                                    // Add coffee shop specific details if found
                                    if (coffeeShop != null) {
                                        putString("photoUrl", coffeeShop.photoUrl)
                                        coffeeShop.rating?.let { putFloat("rating", it) }
                                        putString("placeId", coffeeShop.placeId)
                                        putString("address", coffeeShop.address)
                                    } else {
                                        // Use feed item photo if no coffee shop found
                                        putString("photoUrl", feedItem.photoUrl)
                                    }
                                }
                                findNavController().navigate(R.id.action_userProfileFragment_to_coffeeFragment, bundle)
                            }
                        } catch (e: Exception) {
                            Log.e("UserProfileFragment", "Error fetching coffee shop details", e)
                            // Navigate with basic info if fetch fails
                            val bundle = Bundle().apply {
                                putString("name", feedItem.location?.name ?: "Unknown Location")
                                putString("description", "No description available")
                                putFloat("latitude", feedItem.location?.latitude?.toFloat() ?: 0f)
                                putFloat("longitude", feedItem.location?.longitude?.toFloat() ?: 0f)
                                putString("photoUrl", feedItem.photoUrl)
                                putString("postId", feedItem.id)
                            }
                            findNavController().navigate(R.id.action_userProfileFragment_to_coffeeFragment, bundle)
                        }
                    }
                },
                onCommentClick = { feedItem ->
                    val bundle = Bundle().apply {
                        putString("postId", feedItem.id)
                    }
                    findNavController().navigate(R.id.action_userProfileFragment_to_commentsFragment, bundle)
                },
                showOptionsMenu = true
            )
            
            // Add a post options menu click listener using your existing pattern
            feedAdapter.setPostOptionsClickListener { view, position ->
                showPostOptionsMenu(view, position)
            }
            
            // Set the adapter only after posts are fetched
            recyclerView.adapter = feedAdapter
        }
    }

    private fun deletePostAndComments(postId: String, position: Int) {
        // First, delete all comments associated with the post
        db.collection("Comments")
            .whereEqualTo("postId", postId)
            .get()
            .addOnSuccessListener { comments ->
                // Create a batch to delete all comments
                val batch = db.batch()
                comments.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }

                // Add post deletion to the batch
                batch.delete(db.collection("Posts").document(postId))

                // Commit the batch
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Post and comments deleted successfully", Toast.LENGTH_SHORT).show()
                        // Update the local list and notify adapter
                        if (position < userPosts.size) {
                            userPosts.removeAt(position)
                            feedAdapter.notifyItemRemoved(position)
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Error deleting post: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error deleting comments: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deletePost(postId: String) {
        db.collection("Posts").document(postId)
            .delete()
            .addOnSuccessListener {
                // Remove the deleted post from the local list
                userPosts.removeAll { it.id == postId }
                
                // Notify the adapter of the change
                feedAdapter.notifyDataSetChanged()
                
                // Update the empty state message visibility
                updateEmptyStateVisibility()
                
                Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("UserProfileFragment", "Error deleting post", e)
                Toast.makeText(context, "Failed to delete post", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateEmptyStateVisibility() {
        if (userPosts.isEmpty()) {
            noPostsMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noPostsMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
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

    private fun showPostOptionsMenu(view: View, position: Int) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.post_options_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete_post -> {
                    val postId = userPosts[position].id
                    deletePost(postId)
                    true
                }
                R.id.action_edit_post -> {
                    val bundle = Bundle().apply {
                        putString("postId", userPosts[position].id)
                        putString("postText", userPosts[position].experienceDescription)
                        putString("name", userPosts[position].location?.name)
                        putDouble("latitude", userPosts[position].location?.latitude ?: 0.0)
                        putDouble("longitude", userPosts[position].location?.longitude ?: 0.0)
                        putString("placeId", userPosts[position].location?.placeId)
                        putString("imageUrl", userPosts[position].photoUrl)
                    }
                    findNavController().navigate(R.id.action_userProfileFragment_to_editPostFragment, bundle)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
