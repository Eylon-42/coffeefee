package com.eaor.coffeefee.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.FeedAdapter
import com.eaor.coffeefee.data.AppDatabase
import com.eaor.coffeefee.models.FeedItem
import com.eaor.coffeefee.repositories.FeedRepository
import com.eaor.coffeefee.viewmodels.FeedViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicInteger
import com.google.firebase.firestore.QuerySnapshot
import com.eaor.coffeefee.repositories.CoffeeShopRepository
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.eaor.coffeefee.models.CoffeeShop
import com.eaor.coffeefee.viewmodels.CommentsViewModel
import com.eaor.coffeefee.GlobalState
import com.eaor.coffeefee.CoffeefeeApplication

class FeedFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var feedAdapter: FeedAdapter
    private var savedScrollPosition = 0
    private var savedScrollOffset = 0
    
    // Add ViewModel
    private lateinit var viewModel: FeedViewModel
    // Add SwipeRefreshLayout as class member
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // Change the type of commentListeners from function to ListenerRegistration
    private val commentListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()

    // BroadcastReceiver for profile updates
    private val profileUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.eaor.coffeefee.PROFILE_UPDATED") {
                val userId = intent.getStringExtra("userId")
                Log.d("FeedFragment", "Received profile update broadcast for user: $userId")
                
                if (userId != null) {
                    // Refresh the feed with updated user data
                    viewModel.refreshUserData()
                }
            }
        }
    }
    
    private val feedItems = mutableListOf<FeedItem>()
    private var isLoading = false
    private var isLastPage = false
    
    // Add a BroadcastReceiver to listen for post changes
    private val postChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                if (intent == null) {
                    Log.e("FeedFragment", "Received null intent in postChangeReceiver")
                    return
                }
                
                val action = intent.action
                val postId = intent.getStringExtra("postId")
                
                when (action) {
                    "com.eaor.coffeefee.POST_ADDED" -> {
                        Log.d("FeedFragment", "Received post ADDED broadcast for post: $postId")
                        // We need to refresh posts after a new post is added
                        if (isVisible && isAdded) {
                            viewModel.refreshPosts()
                        } else {
                            // If not visible, set flags to refresh when we become visible
                            GlobalState.shouldRefreshFeed = true
                            // Set a special flag indicating posts were actually changed, not just user data
                            GlobalState.postsWereChanged = true
                        }
                    }
                    "com.eaor.coffeefee.POST_UPDATED" -> {
                        Log.d("FeedFragment", "Received post UPDATED broadcast for post: $postId")
                        // For updates, invalidate the specific post's image if available
                        intent.getStringExtra("photoUrl")?.let { url ->
                            if (url.isNotEmpty()) {
                                com.squareup.picasso.Picasso.get().invalidate(url)
                            }
                        }
                        
                        if (isVisible && isAdded) {
                            viewModel.refreshPosts()
                        } else {
                            // If not visible, set flags to refresh when we become visible
                            GlobalState.shouldRefreshFeed = true
                            // Set a special flag indicating posts were actually changed, not just user data
                            GlobalState.postsWereChanged = true
                        }
                    }
                    "com.eaor.coffeefee.POST_DELETED" -> {
                        Log.d("FeedFragment", "Received post DELETED broadcast for post: $postId")
                        // For deletes, we need to remove the post from the feed
                        if (isVisible && isAdded) {
                            viewModel.refreshPosts()
                        } else {
                            // If not visible, set flags to refresh when we become visible
                            GlobalState.shouldRefreshFeed = true
                            // Set a special flag indicating posts were actually changed, not just user data
                            GlobalState.postsWereChanged = true
                        }
                    }
                    "com.eaor.coffeefee.COMMENT_UPDATED" -> {
                        val commentCount = intent.getIntExtra("commentCount", -1)
                        Log.d("FeedFragment", "Received COMMENT_UPDATED broadcast for post: $postId with count: $commentCount")
                        
                        // Only update if we have both postId and a valid comment count
                        if (postId != null && commentCount >= 0) {
                            Log.d("FeedFragment", "About to update comment count in adapter and ViewModel")
                            // Debug the current adapter state
                            if (::feedAdapter.isInitialized) {
                                val posts = feedAdapter.getItems()
                                val hasPost = posts.any { it.id == postId }
                                Log.d("FeedFragment", "Adapter initialized with ${posts.size} posts. Contains post $postId: $hasPost")
                            } else {
                                Log.e("FeedFragment", "Feed adapter not initialized yet")
                            }
                            
                            updateCommentCount(postId, commentCount)
                        } else {
                            Log.e("FeedFragment", "Invalid postId or commentCount in COMMENT_UPDATED broadcast")
                        }
                    }
                    else -> {
                        Log.d("FeedFragment", "Received unknown broadcast action: $action")
                    }
                }
            } catch (e: Exception) {
                Log.e("FeedFragment", "Error handling post change broadcast: ${e.message}", e)
            }
        }
    }
    
    // Keep track of last visible item for pagination
    private val paginationScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            
            // Skip if not scrolling down
            if (dy <= 0) return
            
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val totalItemCount = layoutManager.itemCount
            val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
            
            // Load more when user is almost at the end (within 2 items)
            if (totalItemCount > 0 && lastVisibleItemPosition >= totalItemCount - 3) {
                Log.d("FeedFragment", "Near bottom (position $lastVisibleItemPosition of $totalItemCount) - loading more posts")
                viewModel.loadMorePosts()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Get the view 
        val view = inflater.inflate(R.layout.fragment_feed, container, false)
        
        // Try to find RecyclerView here, and log if not found
        val foundRecyclerView = view.findViewById<RecyclerView>(R.id.feedRecyclerView)
        if (foundRecyclerView == null) {
            Log.e("FeedFragment", "RecyclerView not found in the layout during onCreateView")
        }
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel with activity scope and AndroidViewModelFactory
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelProvider.AndroidViewModelFactory(requireActivity().application)
        )[FeedViewModel::class.java]

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize SwipeRefreshLayout
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)

        // Initialize UserRepository with Room
        val appDatabase = AppDatabase.getDatabase(requireContext())
        val userDao = appDatabase.userDao()
        val userRepository = com.eaor.coffeefee.repositories.UserRepository(userDao, db)
        
        // Set user repository in the ViewModel
        viewModel.setUserRepository(userRepository)
        
        // Register for profile update events
        registerProfileUpdateReceiver()
        
        // Register for post change events
        registerPostChangeReceiver()

        // Set up RecyclerView
        recyclerView = view.findViewById(R.id.feedRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Set up toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Feed"
        view.findViewById<ImageButton>(R.id.backButton).visibility = View.GONE

        // Initialize FeedAdapter
        if (!::feedAdapter.isInitialized) {
            // Only create a new adapter if we don't already have one
            feedAdapter = FeedAdapter(
                mutableListOf(),
                onMoreInfoClick = { post ->
                // Save scroll position before navigating
                saveScrollPosition()
                
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // Get coffee shop details using the location name or placeId
                        val repository = CoffeeShopRepository.getInstance(requireContext())
                        
                        // First try to get the coffee shop directly by placeId if available
                        val placeId = post.location?.placeId
                        var coffeeShop: CoffeeShop? = null
                        
                        if (placeId != null) {
                            // Try to get shop directly by ID first (most reliable)
                            coffeeShop = repository.getCoffeeShop(placeId)
                        }
                        
                        // If not found by ID, try by name
                        if (coffeeShop == null && post.location?.name != null) {
                            val coffeeShops = repository.getAllCoffeeShops()
                            coffeeShop = coffeeShops.find { shop -> 
                                shop.name == post.location?.name || shop.placeId == post.location?.placeId
                            }
                        }

                        // Make sure we log what caption we're using
                        if (coffeeShop != null) {
                            Log.d("FeedFragment", "Found coffee shop: ${coffeeShop.name}, description: ${coffeeShop.description}")
                        } else {
                            Log.d("FeedFragment", "Coffee shop not found for ${post.location?.name}")
                        }

                        val bundle = Bundle().apply {
                            putString("name", post.location?.name ?: "Unnamed Coffee Shop")
                            putString("description", coffeeShop?.description ?: "No description available") 
                            putFloat("latitude", post.location?.latitude?.toFloat() ?: 0f)
                            putFloat("longitude", post.location?.longitude?.toFloat() ?: 0f)
                            putString("postId", post.id) // Keep the postId for accessing comments
                            
                            // Add coffee shop specific details if found
                            if (coffeeShop != null) {
                                putString("photoUrl", coffeeShop.photoUrl)
                                coffeeShop.rating?.let { putFloat("rating", it) }
                                putString("placeId", coffeeShop.placeId)
                                putString("address", coffeeShop.address ?: "Address not available")
                            } else {
                                // Use feed item photo if no coffee shop found
                                putString("photoUrl", post.photoUrl)
                                putString("address", "Address not available")
                            }
                            
                            // Add source fragment ID
                            putInt("source_fragment_id", R.id.feedFragment)
                        }
                            
                        findNavController().navigate(R.id.action_feedFragment_to_coffeeFragment, bundle)
                    } catch (e: Exception) {
                        Log.e("FeedFragment", "Error fetching coffee shop details", e)
                        // Navigate with basic info if fetch fails
                        val bundle = Bundle().apply {
                            putString("name", post.location?.name ?: "Unknown Location")
                            putString("description", "No description available")
                            putFloat("latitude", post.location?.latitude?.toFloat() ?: 0f)
                            putFloat("longitude", post.location?.longitude?.toFloat() ?: 0f)
                            putString("photoUrl", post.photoUrl)
                            putString("postId", post.id)
                        }
                        findNavController().navigate(R.id.action_feedFragment_to_coffeeFragment, bundle)
                    }
                }
                },
                onCommentClick = { post ->
                    saveScrollPosition()
                    val bundle = Bundle().apply {
                        putString("postId", post.id)
                    }
                findNavController().navigate(R.id.action_feedFragment_to_commentsFragment, bundle)
                },
                showOptionsMenu = false
            )
        }
        
        recyclerView.adapter = feedAdapter

        // Set up scroll listener for pagination
        recyclerView.addOnScrollListener(paginationScrollListener)

        // Add post button
        view.findViewById<FloatingActionButton>(R.id.addPostFab)?.setOnClickListener {
            findNavController().navigate(R.id.action_feedFragment_to_addPostFragment)
        }

        // Set up Swipe to Refresh
        setupSwipeRefresh()

        // Observe ViewModel LiveData
        setupObservers()

        // Restore scroll position from saved instance state
        if (savedInstanceState != null) {
            savedScrollPosition = savedInstanceState.getInt("scroll_position", 0)
            savedScrollOffset = savedInstanceState.getInt("scroll_offset", 0)
            Log.d("FeedFragment", "Restored scroll position: $savedScrollPosition, offset: $savedScrollOffset")
        }

        // Try to load cached posts first
        viewModel.loadCachedPosts()
        
        // Then load from network
        viewModel.loadInitialPosts()
    }

    private fun setupObservers() {
        // Observe posts
        viewModel.feedPosts.observe(viewLifecycleOwner) { posts ->
            Log.d("FeedFragment", "Received ${posts.size} posts from feedPosts LiveData")
            
            // Check for any posts with missing user data and refresh if needed
            val hasMissingUserData = posts.any { 
                it.userName.isNullOrEmpty() || it.userName == "Unknown User" 
            }
            
            if (hasMissingUserData) {
                Log.d("FeedFragment", "Some posts have missing user data, refreshing user data")
                viewModel.refreshUserData(forceRefresh = true)
            }
            
            // Only update adapter if we have posts to show or we're intentionally showing empty state
            if (posts.isNotEmpty()) {
                // Save current scroll position before updating
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val currentFirstVisible = layoutManager.findFirstVisibleItemPosition()
                val currentFirstVisibleOffset = layoutManager.findViewByPosition(currentFirstVisible)?.top ?: 0
                
                // Only scroll to top for new posts if we're already at the top
                val wasAtTop = currentFirstVisible <= 0 && currentFirstVisibleOffset == 0
                
                // Update the adapter with new posts
                feedAdapter.clearAndAddItems(posts)
                
                // Restore scroll position if needed
                if (!wasAtTop && posts.isNotEmpty()) {
                    // If we were explicitly saving position (e.g., for comment navigation)
                    if (savedScrollPosition > 0) {
                        layoutManager.scrollToPositionWithOffset(
                            savedScrollPosition, savedScrollOffset
                        )
                        savedScrollPosition = 0
                        savedScrollOffset = 0
                    } else {
                        // Otherwise try to maintain the current position
                        layoutManager.scrollToPositionWithOffset(
                            currentFirstVisible, currentFirstVisibleOffset
                        )
                    }
                } else if (wasAtTop && posts.isNotEmpty()) {
                    // Only scroll to top if we were already at top and there are posts
                    recyclerView.scrollToPosition(0)
                }
                
                // Ensure Picasso loads and caches images efficiently
                posts.forEach { post ->
                    post.photoUrl?.let { url ->
                        if (url.isNotEmpty()) {
                            // Preload images in the adapter without displaying them yet
                            com.squareup.picasso.Picasso.get()
                                .load(url)
                                .fetch()
                        }
                    }
                }
                
                // Set up comment count listeners
                setupCommentCountListeners()
            } else if (viewModel.isLoading.value != true) {
                // Only clear adapter if we're not in a loading state and posts are actually empty
                feedAdapter.clearAndAddItems(emptyList())
                Log.d("FeedFragment", "Feed is empty and not loading")
            }
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d("FeedFragment", "Loading status changed: $isLoading")
            if (::swipeRefreshLayout.isInitialized) {
                swipeRefreshLayout.isRefreshing = isLoading
            }
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Log.e("FeedFragment", "Error message: $it")
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Fix the setupCommentCountListeners method
    private fun setupCommentCountListeners() {
        // Clear any existing listeners first
        clearCommentListeners()
        
        // Set up listeners for each post in the feed
        for (post in feedAdapter.getItems()) {
            val postId = post.id
            val listener = db.collection("Posts").document(postId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FeedFragment", "Error listening for comment count changes: ${error.message}")
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null && snapshot.exists()) {
                        val newCommentCount = snapshot.getLong("commentCount")?.toInt() ?: 0
                        if (newCommentCount != post.commentCount) {
                            updateCommentCount(postId, newCommentCount)
                            Log.d("FeedFragment", "Real-time comment count updated for post $postId: $newCommentCount")
                        }
                    }
                }
            
            // Store the listener registration object directly
            commentListeners[postId] = listener
        }
    }
    
    private fun clearCommentListeners() {
        for ((_, listener) in commentListeners) {
            listener.remove() // Call remove() instead of invoking the listener
        }
        commentListeners.clear()
    }

    private fun saveScrollPosition() {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        savedScrollPosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleItem = layoutManager.findViewByPosition(savedScrollPosition)
        savedScrollOffset = firstVisibleItem?.top ?: 0
    }

    // Method to update comment count
    fun updateCommentCount(postId: String, count: Int) {
        try {
            Log.d("FeedFragment", "Updating comment count for post $postId to $count")
            
            // First update the comment count in the ViewModel
            viewModel.updateCommentCount(postId, count)
            
            // Then refresh from Room cache to ensure consistency
            lifecycleScope.launch {
                try {
                    // Get the comment dao from the application database
                    val commentDao = (requireActivity().application as CoffeefeeApplication).database.commentDao()
                    
                    // Get the actual count from Room
                    val roomCount = commentDao.getCommentCountForPostSync(postId)
                    
                    // If Room has a different count than what was passed, use the Room count
                    if (roomCount != count) {
                        Log.d("FeedFragment", "Room count ($roomCount) differs from broadcast count ($count), using Room count")
                        viewModel.updateCommentCount(postId, roomCount)
                    }
                    
                    // Always update adapter with the count from Room for consistency
                    if (::feedAdapter.isInitialized) {
                        feedAdapter.updateCommentCount(postId, roomCount)
                        Log.d("FeedFragment", "Updated adapter with Room comment count: $roomCount")
                    } else {
                        Log.e("FeedFragment", "Feed adapter not initialized, can't update comment count")
                    }
                } catch (e: Exception) {
                    Log.e("FeedFragment", "Error getting count from Room: ${e.message}")
                    
                    // Fall back to the passed count if Room query fails
                    if (::feedAdapter.isInitialized) {
                        feedAdapter.updateCommentCount(postId, count)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FeedFragment", "Error in updateCommentCount: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Check if feed needs refreshing from global state
        val needsRefresh = com.eaor.coffeefee.GlobalState.shouldRefreshFeed
        
        Log.d("FeedFragment", "onResume: needsRefresh=$needsRefresh, isVisible=$isVisible")
        
        if (needsRefresh && isVisible) {
            // Reset the flag right away to prevent multiple refreshes
            com.eaor.coffeefee.GlobalState.shouldRefreshFeed = false
            
            Log.d("FeedFragment", "Refreshing feed due to global refresh flag")
            
            // Force a full refresh including latest comments
            viewModel.refreshPosts()
            
            // This will trigger the loading state UI
            if (::swipeRefreshLayout.isInitialized) {
                swipeRefreshLayout.isRefreshing = true
            }
        } else {
            // Even if no global refresh flag, check if user data needs updating
            // This handles the case where user data may have changed but posts remain the same
            viewModel.refreshUserData(forceRefresh = false)
            
            // Always check for state of swipeRefreshLayout to update UI
            if (::swipeRefreshLayout.isInitialized && swipeRefreshLayout.isRefreshing) {
                Log.d("FeedFragment", "SwipeRefreshLayout was refreshing, ensuring refresh continues")
                viewModel.refreshPosts()
            }
        }
        
        // Listen for changes in real-time
        setupCommentCountListeners()
    }
    
    override fun onPause() {
        super.onPause()
        // Clear comment count listeners when pausing to avoid memory leaks
        clearCommentListeners()
        // Save scroll position when leaving the fragment
        saveScrollPosition()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Save current scroll position
        saveScrollPosition()
        
        // Unregister the broadcast receivers
        try {
            requireContext().unregisterReceiver(profileUpdateReceiver)
            requireContext().unregisterReceiver(postChangeReceiver)
            Log.d("FeedFragment", "Unregistered broadcast receivers")
        } catch (e: Exception) {
            Log.e("FeedFragment", "Error unregistering receivers: ${e.message}")
        }
        
        // Clean up comment listeners
        commentListeners.values.forEach { listener -> 
            try {
                listener.remove()
            } catch (e: Exception) {
                Log.e("FeedFragment", "Error removing listener: ${e.message}")
            }
        }
        commentListeners.clear()
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        
        // Save scroll position to instance state
        if (::recyclerView.isInitialized) {
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val position = layoutManager.findFirstVisibleItemPosition()
            val firstVisibleItem = layoutManager.findViewByPosition(position)
            val offset = firstVisibleItem?.top ?: 0
            
            outState.putInt("scroll_position", position)
            outState.putInt("scroll_offset", offset)
            Log.d("FeedFragment", "Saved scroll position: $position, offset: $offset")
        }
    }

    private fun registerProfileUpdateReceiver() {
        try {
            val intentFilter = android.content.IntentFilter("com.eaor.coffeefee.PROFILE_UPDATED")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(profileUpdateReceiver, intentFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                requireContext().registerReceiver(profileUpdateReceiver, intentFilter)
            }
            Log.d("FeedFragment", "Registered broadcast receiver for profile updates")
        } catch (e: Exception) {
            Log.e("FeedFragment", "Error registering receiver: ${e.message}")
        }
    }

    private fun registerPostChangeReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction("com.eaor.coffeefee.POST_ADDED")
                addAction("com.eaor.coffeefee.POST_UPDATED")
                addAction("com.eaor.coffeefee.POST_DELETED")
                addAction("com.eaor.coffeefee.COMMENT_UPDATED")
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(postChangeReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                requireContext().registerReceiver(postChangeReceiver, filter)
            }
            Log.d("FeedFragment", "Registered broadcast receiver for post changes")
        } catch (e: Exception) {
            Log.e("FeedFragment", "Error registering post change receiver: ${e.message}")
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            Log.d("FeedFragment", "SwipeRefresh triggered")
            // Clear any cached data and get fresh data
            viewModel.refreshPosts()
            
            // Also refresh comments if possible
            try {
                val commentViewModel = ViewModelProvider(
                    requireActivity(),
                    ViewModelProvider.AndroidViewModelFactory(requireActivity().application)
                )[CommentsViewModel::class.java]
                
                // Initialize repositories for comment viewmodel if needed
                val appDatabase = AppDatabase.getDatabase(requireContext())
                val commentDao = appDatabase.commentDao()
                val userDao = appDatabase.userDao()
                val userRepository = com.eaor.coffeefee.repositories.UserRepository(userDao, db)
                val commentRepository = com.eaor.coffeefee.repositories.CommentRepository(
                    commentDao, db, userRepository
                )
                
                // Initialize the repositories in the view model
                commentViewModel.initializeRepositories(commentRepository, userRepository)
                
                // Now clear comments
                commentViewModel.clearAllComments()
            } catch (e: Exception) {
                Log.e("FeedFragment", "Error clearing comments during refresh: ${e.message}")
            }
        }
    }
}


