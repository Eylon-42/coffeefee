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
import com.eaor.coffeefee.data.UserEntity
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
import com.eaor.coffeefee.utils.ImageLoader
import android.content.BroadcastReceiver
import android.content.Context
import android.widget.ProgressBar
import com.eaor.coffeefee.viewmodels.FeedViewModel
import kotlinx.coroutines.tasks.await

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
    private lateinit var loadingIndicator: ProgressBar
    
    // Add missing variables
    private var userIdToLoad: String? = null
    private var userId: String? = null
    private var initialDataLoaded = false
    
    private val feedItems = mutableListOf<FeedItem>()
    private var isLoading = false
    private var isLastPage = false
    
    // Track the current user's photo URL
    private var currentUserPhotoUrl: String? = null
    private var currentUserName: String? = null
    
    // Flag to track if we're returning from profile editing
    private var returningFromProfileEdit = false

    private lateinit var handler: Handler
    
    // Add comment listeners map similar to FeedFragment
    private val commentListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
    
    // BroadcastReceiver for post updates
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.eaor.coffeefee.PROFILE_UPDATED" -> {
                    Log.d(TAG, "Received PROFILE_UPDATED broadcast")
                    userIdToLoad?.let { userId ->
                        viewModel.getUserProfile(userId, true)
                    }
                }
                "com.eaor.coffeefee.COMMENT_UPDATED" -> {
                    val postId = intent.getStringExtra("postId")
                    val commentCount = intent.getIntExtra("commentCount", 0)
                    Log.d(TAG, "Received COMMENT_UPDATED broadcast for post: $postId, count: $commentCount")
                    if (postId != null) {
                        viewModel.updateCommentCount(postId, commentCount)
                    }
                }
                "com.eaor.coffeefee.LIKE_UPDATED" -> {
                    val postId = intent.getStringExtra("postId")
                    Log.d(TAG, "Received LIKE_UPDATED broadcast for post: $postId")
                    if (postId != null) {
                        viewModel.updateLikesFromExternal(postId)
                    }
                }
                "com.eaor.coffeefee.POST_ADDED" -> {
                    Log.d(TAG, "Received POST_ADDED broadcast, refreshing profile")
                    viewModel.loadUserPosts(userIdToLoad, true)
                }
            }
        }
    }

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

        // Get user ID from arguments, defaults to current user if none provided
        userIdToLoad = userId ?: auth.currentUser?.uid
        Log.d("UserProfileFragment", "UserID to load: $userIdToLoad")
        
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
        loadingIndicator = view.findViewById(R.id.userDataLoadingIndicator)
        
        // Initialize with empty text to prevent "Unknown User" flash
        userName.text = ""
        userEmail.text = ""
        
        // Show loading state initially
        showLoading(true)
        
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
    }
    
    private fun setupObservers() {
        // Observe user data changes
        viewModel.userData.observe(viewLifecycleOwner) { userData ->
            if (userData != null) {
                // Print userData for debugging
                Log.d("UserProfileFragment", "Received user data: $userData")
                
                // Extract properties safely from map
                val userName = userData["name"] as? String
                val userEmail = userData["email"] as? String
                val profilePhotoUrl = userData["profilePhotoUrl"] as? String
                val uid = userData["uid"] as? String
                
                // Only update if first load or there was an actual edit
                val shouldUpdateUserData = currentUserName == null || 
                                         GlobalState.profileWasEdited ||
                                         (userName != currentUserName) ||
                                         (profilePhotoUrl != currentUserPhotoUrl)
                
                if (shouldUpdateUserData) {
                    // Data loaded successfully, update UI
                    val displayName = userName ?: ""
                    if (displayName.isNotEmpty()) {
                        this.userName.text = displayName
                        currentUserName = displayName
                    }
                    
                    this.userEmail.text = userEmail ?: ""
                    
                    // Load profile image with cache-first approach
                    if (profilePhotoUrl != null && profilePhotoUrl.isNotEmpty()) {
                        Log.d("UserProfileFragment", "Loading profile image: $profilePhotoUrl")
                        currentUserPhotoUrl = profilePhotoUrl
                        
                        // Use our improved ImageLoader for less flickering
                        com.eaor.coffeefee.utils.ImageLoader.loadProfileImage(
                            imageView = userAvatar,
                            imageUrl = profilePhotoUrl,
                            forceRefresh = GlobalState.profileWasEdited // Force refresh if profile was edited
                        )
                    } else {
                        userAvatar.setImageResource(R.drawable.default_avatar)
                    }
                    
                    // Reset the edit flag after applying changes
                    if (GlobalState.profileWasEdited) {
                        GlobalState.profileWasEdited = false
                    }
                } else {
                    Log.d("UserProfileFragment", "Skipping user data update - no changes detected")
                }
                
                // Hide loading indicator
                showLoading(false)
                
                // Mark that we've loaded data at least once
                initialDataLoaded = true
                
                // Show/hide edit button based on if this is the current user
                editButton.visibility = if (uid == auth.currentUser?.uid) View.VISIBLE else View.GONE
            } else {
                // No data available, show default state but NOT "Unknown User"
                showLoading(false)
                if (this.userName.text.isNullOrEmpty()) {
                    this.userName.text = ""  // Empty instead of "User Not Found"
                }
                this.userEmail.text = ""
                userAvatar.setImageResource(R.drawable.default_avatar)
                editButton.visibility = View.GONE
            }
        }
        
        // Observe loading state to show/hide progress indicator
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Only show loading state if we haven't loaded data yet
            if (!initialDataLoaded) {
                showLoading(isLoading)
            }
        }
        
        // Observe user posts
        viewModel.userPosts.observe(viewLifecycleOwner) { posts ->
            if (posts.isEmpty()) {
                recyclerView.visibility = View.GONE
                noPostsMessage.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                noPostsMessage.visibility = View.GONE
                
                // Update the adapter with the posts
                feedAdapter.clearAndAddItems(posts)
            }
        }
        
        // Observe errors
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
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
        
        // Check if the current user profile needs to be refreshed
        checkAndRefreshProfile()
        
        // Register the broadcast receivers
        registerReceivers()
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister the broadcast receivers to prevent leaks
        unregisterReceivers()
    }
    
    private fun registerReceivers() {
        // Register for all relevant updates
        val intentFilter = IntentFilter().apply {
            addAction("com.eaor.coffeefee.PROFILE_UPDATED")
            addAction("com.eaor.coffeefee.COMMENT_UPDATED")
            addAction("com.eaor.coffeefee.LIKE_UPDATED")
            addAction("com.eaor.coffeefee.POST_ADDED")
        }
        
        // On Android 13+ (API 33+), we need to specify RECEIVER_NOT_EXPORTED flag
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(broadcastReceiver, intentFilter)
        }
        
        Log.d(TAG, "Broadcast receivers registered")
    }
    
    private fun unregisterReceivers() {
        try {
            requireContext().unregisterReceiver(broadcastReceiver)
            Log.d("UserProfileFragment", "Broadcast receivers unregistered")
        } catch (e: Exception) {
            // Ignore if receivers weren't registered
            Log.d("UserProfileFragment", "Error unregistering receivers: ${e.message}")
        }
    }

    private fun checkAndRefreshProfile() {
        // Get the user ID to work with
        val userIdToLoad = userId ?: auth.currentUser?.uid
        
        if (userIdToLoad != null) {
            // Always check for profile updates
            val shouldRefreshProfile = GlobalState.shouldRefreshProfile || 
                                     GlobalState.profileWasEdited || 
                                     GlobalState.profileDataChanged
            
            // Check if data needs to be loaded for the first time
            if (!initialDataLoaded) {
                // First time loading - show loading indicator
                showLoading(true)
                userIdToLoad?.let { loadUserProfile(it, forceRefresh = false) }
                viewModel.loadUserPosts(userIdToLoad, forceRefresh = false)
                initialDataLoaded = true
            } 
            // Only refresh data if explicitly requested by global state flags
            else if (shouldRefreshProfile || GlobalState.shouldRefreshFeed) {
                // Show loading indicator first
                showLoading(true)
                
                // Reset all flags right away to prevent multiple refreshes
                GlobalState.shouldRefreshProfile = false
                GlobalState.shouldRefreshFeed = false
                GlobalState.profileDataChanged = false 
                GlobalState.postsWereChanged = false
                
                // Reload both profile data and posts
                userIdToLoad?.let { loadUserProfile(it, forceRefresh = true) }
                viewModel.loadUserPosts(userIdToLoad, forceRefresh = true)
                
                Log.d(TAG, "Profile and feed data refresh triggered")
            }
        }
        
        // Setup comment count listeners regardless of refresh state
        setupCommentCountListeners()
    }

    private fun loadUserProfile(id: String, forceRefresh: Boolean = false) {
        try {
            userId = id
            Log.d("UserProfileFragment", "Loading profile for user $id, forceRefresh=$forceRefresh")
            
            // Show loading indicator
            loadingIndicator.visibility = View.VISIBLE
            
            // Check if GlobalState indicates we should force a refresh
            val shouldForceRefresh = forceRefresh || GlobalState.profileDataChanged || GlobalState.shouldRefreshProfile
            
            if (shouldForceRefresh) {
                Log.d("UserProfileFragment", "Forcing refresh due to global state flags")
                // Reset flags immediately to prevent duplicate refreshes
                GlobalState.profileDataChanged = false
                GlobalState.shouldRefreshProfile = false
            }
            
            // Clear previous data to show loading state
            userName.text = ""
            userEmail.text = ""
            
            // Load user profile with potential force refresh
            viewModel.getUserProfile(id, forceRefresh = shouldForceRefresh)
            
            // Also load user posts with same refresh settings
            viewModel.loadUserPosts(id, forceRefresh = shouldForceRefresh)
            
        } catch (e: Exception) {
            Log.e("UserProfileFragment", "Error loading profile: ${e.message}")
            Toast.makeText(context, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            loadingIndicator.visibility = View.GONE
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
    fun updateCommentCount(postId: String, count: Int) {
        try {
            Log.d("UserProfileFragment", "Updating comment count for post $postId to $count")
            
            // First update the comment count in the ViewModel
            viewModel.updateCommentCount(postId, count)
            
            // Then refresh from Room cache to ensure consistency
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Get the comment dao from the application database
                    val commentDao = (requireActivity().application as com.eaor.coffeefee.CoffeefeeApplication).database.commentDao()
                    
                    // Update the database directly to ensure consistency
                    commentDao.updateCommentCountForPost(postId, count)
                    
                    // Get the actual count from Room
                    val roomCount = commentDao.getCommentCountForPostSync(postId)
                    
                    Log.d("UserProfileFragment", "Room comment count for post $postId: $roomCount")
                    
                    // Always update the adapter with the latest count
                    if (::feedAdapter.isInitialized) {
                        val posts = viewModel.userPosts.value ?: return@launch
                        val position = posts.indexOfFirst { it.id == postId }
                        
                        if (position >= 0) {
                            Log.d("UserProfileFragment", "Updating post at position $position with comment count $roomCount")
                            // Update the post object directly
                            posts[position].commentCount = roomCount
                            // Notify adapter of the change
                            feedAdapter.notifyItemChanged(position)
                        } else {
                            Log.d("UserProfileFragment", "Post $postId not found in user posts list")
                        }
                    } else {
                        Log.e("UserProfileFragment", "Feed adapter not initialized")
                    }
                } catch (e: Exception) {
                    Log.e("UserProfileFragment", "Error updating comment count: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("UserProfileFragment", "Error in updateCommentCount: ${e.message}", e)
        }
    }

    // Add helper method to show/hide loading state
    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            loadingIndicator.visibility = View.VISIBLE
            userName.visibility = View.INVISIBLE
            userEmail.visibility = View.INVISIBLE
        } else {
            loadingIndicator.visibility = View.GONE
            userName.visibility = View.VISIBLE
            userEmail.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val TAG = "UserProfileFragment"
    }
}
