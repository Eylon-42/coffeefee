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
import com.eaor.coffeefee.fragments.UserProfileFragment
import com.eaor.coffeefee.repositories.UserRepository

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
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.eaor.coffeefee.PROFILE_UPDATED" -> {
                    Log.d(TAG, "Received PROFILE_UPDATED broadcast")
                    viewModel.refreshUserData(true)
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
                    Log.d(TAG, "Received POST_ADDED broadcast, refreshing feed")
                    viewModel.refreshPosts()
                }
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

    // Near the top of the class, add the userRepository field
    private lateinit var userRepository: UserRepository
    private lateinit var feedRepository: FeedRepository

    companion object {
        private const val TAG = "FeedFragment"
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

        // Initialize Repository
        val database = AppDatabase.getDatabase(requireContext())
        userRepository = UserRepository(database.userDao(), db)
        feedRepository = FeedRepository(database.feedItemDao(), db, userRepository)
        
        // Set user repository in the ViewModel
        viewModel.setUserRepository(userRepository)
        
        // Register for profile update events
        registerReceivers()

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
            
            // Then update Room database and refresh UI
            lifecycleScope.launch {
                try {
                    // Get the comment dao from the application database
                    val commentDao = (requireActivity().application as CoffeefeeApplication).database.commentDao()
                    
                    // Update the database directly to ensure consistency
                    commentDao.updateCommentCountForPost(postId, count)
                    
                    // Get the actual count from Room to confirm
                    val roomCount = commentDao.getCommentCountForPostSync(postId)
                    
                    Log.d("FeedFragment", "Room comment count for post $postId: $roomCount")
                    
                    // Update the UI with the new count
                    if (::feedAdapter.isInitialized) {
                        val posts = feedAdapter.getItems()
                        val position = posts.indexOfFirst { it.id == postId }
                        
                        if (position >= 0) {
                            Log.d("FeedFragment", "Updating post at position $position with comment count $roomCount")
                            // Update the post object directly
                            posts[position].commentCount = roomCount
                            // Notify adapter of the change with a payload to prevent full rebind
                            feedAdapter.notifyItemChanged(position, com.eaor.coffeefee.adapters.FeedAdapter.COMMENT_COUNT)
                        } else {
                            Log.d("FeedFragment", "Post $postId not found in feed posts")
                        }
                    } else {
                        Log.e("FeedFragment", "Feed adapter not initialized")
                    }
                } catch (e: Exception) {
                    Log.e("FeedFragment", "Error updating comment count: ${e.message}", e)
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
        val profileDataChanged = com.eaor.coffeefee.GlobalState.profileDataChanged
        val postsChanged = com.eaor.coffeefee.GlobalState.postsWereChanged
        val profileWasEdited = com.eaor.coffeefee.GlobalState.profileWasEdited
        
        Log.d("FeedFragment", "onResume: needsRefresh=$needsRefresh, profileDataChanged=$profileDataChanged, postsChanged=$postsChanged, profileWasEdited=$profileWasEdited, isVisible=$isVisible")
        
        // Check if a profile edit was made and update user data if needed
        if ((profileWasEdited || profileDataChanged) && isVisible) {
            Log.d("FeedFragment", "Updating user data due to profile edit")
            updateUserData()
        }
        
        // Check if posts changed and need refreshing
        if ((needsRefresh || postsChanged) && isVisible) {
            Log.d("FeedFragment", "Refreshing feed due to global flag")
            refreshFeed()
        }
        
        // Register broadcast receivers
        registerReceivers()
    }
    
    override fun onPause() {
        super.onPause()
        
        // Save scroll position for restoration later
        saveScrollPosition()
        
        // Unregister broadcast receivers
        unregisterReceivers()
        
        // Clear comment count listeners
        clearCommentListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Save current scroll position
        saveScrollPosition()
        
        // Unregister the broadcast receivers
        try {
            requireContext().unregisterReceiver(broadcastReceiver)
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

    private fun registerReceivers() {
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
            Log.d(TAG, "Broadcast receivers unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers: ${e.message}")
        }
    }

    private fun refreshFeed() {
        // Reset the flags right away to prevent multiple refreshes
        com.eaor.coffeefee.GlobalState.shouldRefreshFeed = false
        
        if (com.eaor.coffeefee.GlobalState.postsWereChanged) {
            // Only do a full refresh if actual posts were changed
            Log.d("FeedFragment", "Refreshing feed due to post content changes")
            com.eaor.coffeefee.GlobalState.postsWereChanged = false
            
            // Force a full refresh including latest comments
            viewModel.refreshPosts()
            
            // This will trigger the loading state UI
            if (::swipeRefreshLayout.isInitialized) {
                swipeRefreshLayout.isRefreshing = true
            }
        } else if (com.eaor.coffeefee.GlobalState.profileDataChanged) {
            // If only profile data changed, just refresh the user data without reloading posts
            Log.d("FeedFragment", "Refreshing only user data due to profile changes")
            com.eaor.coffeefee.GlobalState.profileDataChanged = false
            viewModel.refreshUserData(forceRefresh = true)
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

    private fun updateUserData() {
        try {
            Log.d("FeedFragment", "Updating user data in feed")
            
            // Reset the flag to prevent duplicate refreshes
            GlobalState.profileDataChanged = false
            GlobalState.profileWasEdited = false
            
            // Force refresh user data in posts to ensure latest profile changes are shown
            viewModel.refreshUserData(forceRefresh = true)
        } catch (e: Exception) {
            Log.e("FeedFragment", "Error updating user data: ${e.message}")
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshPosts()
        }
    }
}


