package com.eaor.coffeefee.fragments

import android.app.AlertDialog
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.AuthActivity
import com.eaor.coffeefee.R
import com.eaor.coffeefee.viewmodels.ProfileViewModel
import com.eaor.coffeefee.adapters.FeedAdapter
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.models.FeedItem
import com.eaor.coffeefee.repositories.FeedRepository
import com.eaor.coffeefee.repositories.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.eaor.coffeefee.data.User
import com.squareup.picasso.Picasso
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.NetworkPolicy
import com.eaor.coffeefee.utils.CircleTransform
import com.eaor.coffeefee.GlobalState

import com.eaor.coffeefee.repositories.CoffeeShopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.QuerySnapshot
import com.eaor.coffeefee.models.CoffeeShop
import com.google.firebase.firestore.Query
import android.view.Menu
import android.graphics.Typeface
import java.util.*
import java.text.SimpleDateFormat
import java.util.Locale

class UserProfileFragment : Fragment() {
    private lateinit var viewModel: ProfileViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userRepository: UserRepository
    private lateinit var feedRepository: FeedRepository
    private lateinit var repository: CoffeeShopRepository
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var recyclerView: RecyclerView
    private lateinit var feedAdapter: FeedAdapter
    private val userPosts = mutableListOf<FeedItem>()
    private lateinit var noPostsMessage: TextView
    private lateinit var userName: TextView
    private lateinit var userEmail: TextView
    private lateinit var userAvatar: ImageView
    private lateinit var editButton: ImageButton
    
    private val feedItems = mutableListOf<FeedItem>()
    private var isLoading = false
    private var isLastPage = false
    
    // Track the current user's photo URL
    private var currentUserPhotoUrl: String? = null
    
    // Flag to track if we're returning from profile editing
    private var returningFromProfileEdit = false

    private lateinit var handler: Handler
    
    // Add comment listeners map similar to FeedFragment
    private val commentListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
    
    // BroadcastReceiver for post updates
    private val postUpdateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent == null) return
            
            when (intent.action) {
                "com.eaor.coffeefee.POST_UPDATED" -> {
                    val postId = intent.getStringExtra("postId") ?: return
                    Log.d("UserProfileFragment", "Received POST_UPDATED broadcast for post: $postId")
                    
                    // Refresh all posts to ensure consistent data
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        viewModel.refreshUserPosts(currentUser.uid)
                    }
                }
                "com.eaor.coffeefee.PROFILE_UPDATED" -> {
                    val userId = intent.getStringExtra("userId") ?: return
                    Log.d("UserProfileFragment", "Received PROFILE_UPDATED broadcast for user: $userId")
                    
                    // Check if this is the current user
                    val currentUser = auth.currentUser
                    if (currentUser != null && currentUser.uid == userId) {
                        // Force profile refresh
                        loadUserProfile(userId)
                    }
                }
                "com.eaor.coffeefee.COMMENT_UPDATED" -> {
                    val postId = intent.getStringExtra("postId") ?: return
                    val commentCount = intent.getIntExtra("commentCount", -1)
                    Log.d("UserProfileFragment", "Received COMMENT_UPDATED broadcast for post: $postId with count: $commentCount")
                    
                    // Update the comment count if it was provided
                    if (commentCount >= 0) {
                        updateCommentCount(postId, commentCount)
                    }
                }
            }
        }
    }

    // Constant for logging
    private val TAG = "UserProfileFragment"

    // Add NavArgs property
    private var userId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get user ID from arguments if available
        userId = arguments?.getString("userId")
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(requireActivity().application)
        )[ProfileViewModel::class.java]
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val userDao = AppDatabase.getDatabase(requireContext()).userDao()
        userRepository = UserRepository(userDao, db)
        
        // Initialize FeedRepository
        val feedItemDao = AppDatabase.getDatabase(requireContext()).feedItemDao()
        feedRepository = FeedRepository(feedItemDao, db, userRepository)
        viewModel.setRepository(feedRepository)
        viewModel.setUserRepository(userRepository)
        
        // Initialize CoffeeShopRepository
        repository = CoffeeShopRepository.getInstance(requireContext())
        
        // Setup views
        setupViews(view)

        handler = Handler(Looper.getMainLooper())
    }
    
    private fun setupViews(view: View) {
        // Set up toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Profile"
        view.findViewById<ImageButton>(R.id.backButton).visibility = View.GONE

        // Initialize views
        userName = view.findViewById(R.id.userName)
        userEmail = view.findViewById(R.id.userEmail)
        userAvatar = view.findViewById(R.id.userAvatar)
        recyclerView = view.findViewById(R.id.postsRecyclerView)
        editButton = view.findViewById(R.id.editButton)
        noPostsMessage = view.findViewById(R.id.noPostsMessage)
        val joinDate = view.findViewById<TextView>(R.id.joinDate)
        
        // Set up edit button with dropdown menu
        editButton.setOnClickListener {
            showDropdownMenu(it)
        }
        
        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Initialize FeedAdapter
        feedAdapter = FeedAdapter(
            mutableListOf(),
            onMoreInfoClick = { post ->
                // Navigate to coffee shop details
                val bundle = Bundle().apply {
                    putString("name", post.location?.name ?: "Unknown Location")
                    putString("description", "No description available")
                    putFloat("latitude", post.location?.latitude?.toFloat() ?: 0f)
                    putFloat("longitude", post.location?.longitude?.toFloat() ?: 0f)
                    putString("photoUrl", post.photoUrl)
                    putString("postId", post.id)
                    
                    // Add source fragment ID
                    putInt("source_fragment_id", R.id.userProfileFragment)
                }
                findNavController().navigate(R.id.action_userProfileFragment_to_coffeeFragment, bundle)
            },
            onCommentClick = { post ->
                val bundle = Bundle().apply {
                    putString("postId", post.id)
                }
                
                // Check the current destination ID before navigating
                val currentDestId = findNavController().currentDestination?.id
                if (currentDestId == R.id.profileTab) {
                    // If we're in the tab version, add the action to that tab's navigation
                    // The proper action for the profile tab needs to be added to nav_graph.xml
                    try {
                        // Try to use the main profile's action
                        findNavController().navigate(R.id.commentsFragment, bundle)
                    } catch (e: Exception) {
                        Log.e("UserProfileFragment", "Navigation error: ${e.message}")
                        // Fallback to simpler navigation without animation
                        findNavController().navigate(R.id.commentsFragment, bundle)
                    }
                } else {
                    // Default navigation for userProfileFragment
                    findNavController().navigate(R.id.action_userProfileFragment_to_commentsFragment, bundle)
                }
            },
            showOptionsMenu = true
        )
        
        // Add a post options menu click listener
        feedAdapter.setPostOptionsClickListener { view, position ->
            val currentPosts = viewModel.userPosts.value ?: listOf()
            if (position < currentPosts.size) {
                showPostOptionsMenu(view, currentPosts[position])
            }
        }
        
        recyclerView.adapter = feedAdapter
        
        // Set up observers
        setupObservers()
        
        // Load current user's data
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val user = userRepository.getUserData(currentUser.uid)
                    
                    if (user != null) {
                        // Set user data from repository
                        userName.text = user.name
                        userEmail.text = user.email
                        
                        // Set join date (using account creation time if available)
                        val joinDateString = if (currentUser.metadata != null) {
                            val creationTimestamp = currentUser.metadata?.creationTimestamp ?: 0
                            val date = Date(creationTimestamp)
                            val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                            dateFormat.format(date)
                        } else {
                            "Unknown"
                        }
                        joinDate.text = joinDateString
                        
                        // Load profile image using Picasso with normal caching
                        if (!user.profilePhotoUrl.isNullOrEmpty()) {
                            // Store the current photo URL
                            currentUserPhotoUrl = user.profilePhotoUrl
                            
                            // Use our centralized ImageLoader
                            com.eaor.coffeefee.utils.ImageLoader.loadProfileImage(
                                userAvatar,
                                user.profilePhotoUrl,
                                R.drawable.default_avatar,
                                true
                            )
                        } else {
                            userAvatar.setImageResource(R.drawable.default_avatar)
                            userAvatar.tag = null
                            currentUserPhotoUrl = null
                        }
                    } else {
                        // No user found, fallback to basic auth data
                        userName.text = currentUser.displayName ?: "User"
                        userEmail.text = currentUser.email ?: ""
                        
                        // Load default avatar using Picasso for consistency
                        Picasso.get()
                            .load(R.drawable.default_avatar)
                            .into(userAvatar)
                        
                        // Set a default join date
                        joinDate.text = "New member"
                    }
                    
                    // Load user posts
                    viewModel.loadUserPosts(currentUser.uid)
                } catch (e: Exception) {
                    Log.e("UserProfileFragment", "Error loading user data: ${e.message}")
                    Toast.makeText(context, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
                    
                    // Try to fetch posts even if user data fails
                    viewModel.loadUserPosts(currentUser.uid)
                }
            }
        }
    }
    
    private fun setupObservers() {
        // Observe posts
        viewModel.userPosts.observe(viewLifecycleOwner) { posts ->
            if (posts.isEmpty()) {
                noPostsMessage.visibility = View.VISIBLE
                noPostsMessage.text = "No posts available"
            } else {
                noPostsMessage.visibility = View.GONE
                feedAdapter.updateItems(posts.toMutableList())
            }
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show loading indicator if needed
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showPostOptionsMenu(view: View, post: FeedItem) {
        val popupMenu = PopupMenu(requireContext(), view)
        val inflater: MenuInflater = popupMenu.menuInflater
        inflater.inflate(R.menu.post_options_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit_post -> {
                    val bundle = Bundle().apply {
                        putString("postId", post.id)
                        putString("description", post.experienceDescription)
                        putString("photoUrl", post.photoUrl)
                        putString("locationName", post.location?.name)
                        putDouble("latitude", post.location?.latitude ?: 0.0)
                        putDouble("longitude", post.location?.longitude ?: 0.0)
                    }
                    findNavController().navigate(R.id.action_userProfileFragment_to_editPostFragment, bundle)
                    true
                }
                R.id.action_delete_post -> {
                    showDeleteConfirmationDialog(post.id)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }
    
    private fun showDeleteConfirmationDialog(postId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete") { _, _ ->
                // Display loading indicator
                val progressDialog = android.app.ProgressDialog(context).apply {
                    setMessage("Deleting post...")
                    setCancelable(false)
                    show()
                }
                
                // Delete post using ViewModel
                viewModel.deletePost(postId)
                
                // Find the index of the post in the current feed list before it's deleted from ViewModel
                val currentPosts = viewModel.userPosts.value ?: emptyList()
                val position = currentPosts.indexOfFirst { it.id == postId }
                
                // Remove the post from the adapter immediately for responsive UI
                if (position != -1) {
                    val updatedPosts = currentPosts.toMutableList()
                    updatedPosts.removeAt(position)
                    
                    // Update adapter with the new list
                    feedAdapter.updateItems(updatedPosts)
                    
                    // If no posts left, show empty state
                    if (updatedPosts.isEmpty()) {
                        noPostsMessage.visibility = View.VISIBLE
                    }
                }
                
                // Dismiss progress dialog after a short delay
                handler.postDelayed({
                    try {
                        if (progressDialog.isShowing) {
                            progressDialog.dismiss()
                        }
                    } catch (e: Exception) {
                        Log.e("UserProfileFragment", "Error dismissing dialog: ${e.message}")
                    }
                }, 500)
                
                // Explicitly trigger a feed refresh by sending a broadcast
                try {
                    val intent = Intent("com.eaor.coffeefee.POST_DELETED").apply {
                        putExtra("postId", postId)
                    }
                    requireContext().sendBroadcast(intent)
                    Log.d("UserProfileFragment", "Sent POST_DELETED broadcast for post $postId")
                } catch (e: Exception) {
                    Log.e("UserProfileFragment", "Error sending broadcast: ${e.message}")
                }
                
                // Set global refresh flag
                GlobalState.triggerRefreshAfterContentChange()
                GlobalState.shouldRefreshFeed = true
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEmptyStateVisibility(posts: List<FeedItem>) {
        if (posts.isEmpty()) {
            noPostsMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noPostsMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // We don't need the toolbar menu, using our own button
        // setHasOptionsMenu(true)
    }

    private fun showDropdownMenu(view: View) {
        val popupMenu = PopupMenu(requireContext(), view)
        val inflater: MenuInflater = popupMenu.menuInflater
        inflater.inflate(R.menu.profile_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit_profile -> {
                    // Set flag to indicate we're going to edit profile
                    returningFromProfileEdit = true
                    
                    // Create bundle with current user data
                    val bundle = Bundle().apply {
                        putString("userName", userName.text.toString())
                        putString("userEmail", userEmail.text.toString())
                        putString("userPhotoUrl", currentUserPhotoUrl)
                    }
                    
                    // Navigate to profile fragment with user data
                    findNavController().navigate(R.id.action_userProfileFragment_to_profileFragment, bundle)
                    true
                }
                R.id.action_logout -> {
                    auth.signOut()
                    Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(requireContext(), AuthActivity::class.java)
                    startActivity(intent)
                    requireActivity().finish()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    override fun onResume() {
        super.onResume()
        
        // Register broadcast receiver
        val intentFilter = IntentFilter().apply {
            addAction("com.eaor.coffeefee.POST_UPDATED")
            addAction("com.eaor.coffeefee.PROFILE_UPDATED")
            addAction("com.eaor.coffeefee.COMMENT_UPDATED")
        }
        
        // Use the appropriate API based on Android version
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(
                postUpdateReceiver, 
                intentFilter,
                android.content.Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            requireContext().registerReceiver(postUpdateReceiver, intentFilter)
        }
        
        // Check if we need to refresh from global state
        val needsRefresh = com.eaor.coffeefee.GlobalState.shouldRefreshProfile
        
        if (needsRefresh && isVisible) {
            // Reset the flag right away to prevent multiple refreshes
            com.eaor.coffeefee.GlobalState.shouldRefreshProfile = false
            
            Log.d("UserProfileFragment", "Refreshing profile due to global refresh flag")
            
            val userIdToRefresh = userId ?: auth.currentUser?.uid
            if (userIdToRefresh != null) {
                // Force refresh user profile
                loadUserProfile(userIdToRefresh)
                // Force refresh all posts to update comment counts - remove forceRefresh parameter
                viewModel.refreshUserPosts(userIdToRefresh)
            }
        } else {
            // Even if not doing a full refresh, check for missing data
            val userIdToCheck = userId ?: auth.currentUser?.uid
            if (userIdToCheck != null && (userName.text.isNullOrEmpty() || userName.text == "Loading..." || userName.text == "Unknown User")) {
                Log.d("UserProfileFragment", "Loading initial user profile data due to missing/placeholder data")
                loadUserProfile(userIdToCheck)
            }
            
            // Check if posts are missing and load them if needed
            if (viewModel.userPosts.value.isNullOrEmpty()) {
                userIdToCheck?.let { viewModel.loadUserPosts(it) }
            }
        }
        
        // Setup comment count listeners regardless of refresh state
        setupCommentCountListeners()
    }
    
    override fun onPause() {
        super.onPause()
        
        // Clear comment count listeners when pausing to avoid memory leaks
        clearCommentListeners()
        
        // Unregister the broadcast receiver to avoid memory leaks
        try {
            requireContext().unregisterReceiver(postUpdateReceiver)
            Log.d("UserProfileFragment", "Unregistered broadcast receiver")
        } catch (e: Exception) {
            Log.e("UserProfileFragment", "Error unregistering receiver: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Clean up comment listeners
        clearCommentListeners()
    }

    private fun loadUserProfile(userId: String) {
        Log.d("UserProfileFragment", "Loading profile for user $userId")
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Try to get user from Room first, then Firestore
                val user = userRepository.getUserData(userId, forceRefresh = true)
                
                if (user != null) {
                    Log.d("UserProfileFragment", "Loaded user: ${user.name}, ${user.profilePhotoUrl}")
                    
                    // Update UI with user data
                    userName.text = user.name
                    userEmail.text = user.email
                    
                    // Store the current photo URL for sharing with other screens
                    currentUserPhotoUrl = user.profilePhotoUrl
                    
                    // Update profile image if it exists
                    if (!user.profilePhotoUrl.isNullOrEmpty()) {
                        Log.d("UserProfileFragment", "Loading profile photo: ${user.profilePhotoUrl}")
                        
                        // Use our centralized ImageLoader with proper caching strategy
                        com.eaor.coffeefee.utils.ImageLoader.loadProfileImage(
                            userAvatar,
                            user.profilePhotoUrl,
                            R.drawable.default_avatar,
                            true
                        )
                    } else {
                        userAvatar.setImageResource(R.drawable.default_avatar)
                        userAvatar.tag = null
                    }
                    
                    // Ensure we have the latest posts for this user
                    Log.d("UserProfileFragment", "Refreshing posts after profile load")
                    viewModel.refreshUserPosts(userId)
                } else {
                    Log.e("UserProfileFragment", "Failed to load user profile")
                    userName.text = "Unknown User"
                    userEmail.text = ""
                    userAvatar.setImageResource(R.drawable.default_avatar)
                    userAvatar.tag = null
                    
                    // Still try to load posts
                    viewModel.loadUserPosts(userId)
                }
            } catch (e: Exception) {
                Log.e("UserProfileFragment", "Error loading user profile: ${e.message}")
                Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Add method to set up comment count listeners
    private fun setupCommentCountListeners() {
        // Clear any existing listeners first
        clearCommentListeners()
        
        // Only proceed if we have the adapter and it has items
        if (!::feedAdapter.isInitialized) return
        
        // Set up listeners for each post
        for (post in feedAdapter.getItems()) {
            val postId = post.id
            val listener = db.collection("Posts").document(postId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("UserProfileFragment", "Error listening for comment count changes: ${error.message}")
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null && snapshot.exists()) {
                        val newCommentCount = snapshot.getLong("commentCount")?.toInt() ?: 0
                        if (newCommentCount != post.commentCount) {
                            updateCommentCount(postId, newCommentCount)
                            Log.d("UserProfileFragment", "Real-time comment count updated for post $postId: $newCommentCount")
                        }
                    }
                }
            
            // Store the listener registration
            commentListeners[postId] = listener
        }
    }
    
    // Add method to clear comment listeners
    private fun clearCommentListeners() {
        for ((_, listener) in commentListeners) {
            try {
                listener.remove()
            } catch (e: Exception) {
                Log.e("UserProfileFragment", "Error removing listener: ${e.message}")
            }
        }
        commentListeners.clear()
    }
    
    // Add method to update comment count
    private fun updateCommentCount(postId: String, count: Int) {
        try {
            Log.d("UserProfileFragment", "Updating comment count for post $postId to $count")
            
            // First update the count in ViewModel
            viewModel.updateCommentCount(postId, count)
            
            // Then refresh from Room cache to ensure consistency
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Get the comment dao from the application database
                    val commentDao = (requireActivity().application as com.eaor.coffeefee.CoffeefeeApplication).database.commentDao()
                    
                    // Get the actual count from Room
                    val roomCount = commentDao.getCommentCountForPostSync(postId)
                    
                    // If Room has a different count than what was passed, use the Room count
                    if (roomCount != count) {
                        Log.d("UserProfileFragment", "Room count ($roomCount) differs from broadcast count ($count), using Room count")
                        viewModel.updateCommentCount(postId, roomCount)
                    }
                    
                    // Always update adapter with the count from Room for consistency
                    if (::feedAdapter.isInitialized) {
                        val items = feedAdapter.getItems()
                        val postIndex = items.indexOfFirst { it.id == postId }
                        if (postIndex != -1) {
                            Log.d("UserProfileFragment", "Updating adapter with Room comment count: $roomCount for post at position $postIndex")
                            items[postIndex].commentCount = roomCount
                            feedAdapter.notifyItemChanged(postIndex, com.eaor.coffeefee.adapters.FeedAdapter.COMMENT_COUNT)
                        } else {
                            Log.e("UserProfileFragment", "Post $postId not found in adapter items")
                        }
                    } else {
                        Log.e("UserProfileFragment", "Feed adapter not initialized, can't update comment count")
                    }
                } catch (e: Exception) {
                    Log.e("UserProfileFragment", "Error getting count from Room: ${e.message}")
                    // Just update with the passed count if Room query fails
                    viewModel.updateCommentCount(postId, count)
                }
            }
        } catch (e: Exception) {
            Log.e("UserProfileFragment", "Error in updateCommentCount: ${e.message}", e)
            // Fallback to simple update
            viewModel.updateCommentCount(postId, count)
        }
    }
}
