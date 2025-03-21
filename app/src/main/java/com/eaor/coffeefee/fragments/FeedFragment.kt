package com.eaor.coffeefee.fragments

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
import com.eaor.coffeefee.repository.FeedRepository
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

class FeedFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var feedAdapter: FeedAdapter
    private lateinit var feedRepository: FeedRepository
    private var savedScrollPosition = 0
    private var savedScrollOffset = 0
    
    // Add ViewModel
    private lateinit var viewModel: FeedViewModel

    // Change the type of commentListeners from function to ListenerRegistration
    private val commentListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()

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

        // Initialize ViewModel with activity scope so it survives navigation
        viewModel = ViewModelProvider(requireActivity())[FeedViewModel::class.java]

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize FeedRepository with Room
        val feedItemDao = AppDatabase.getDatabase(requireContext()).feedItemDao()
        feedRepository = FeedRepository(feedItemDao, db)
        viewModel.setRepository(feedRepository)

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
                        val repository = CoffeeShopRepository.getInstance()
                        
                        // First try to get the coffee shop directly by placeId if available
                        val placeId = post.location?.placeId
                        var coffeeShop: CoffeeShop? = null
                        
                        if (placeId != null) {
                            // Try to get shop directly by ID first (most reliable)
                            coffeeShop = repository.getCoffeeShop(placeId)
                        }
                        
                        // If not found by ID, try by name
                        if (coffeeShop == null && post.location?.name != null) {
                            val coffeeShops = repository.getAllCoffeeShops().first()
                            coffeeShop = coffeeShops.find { shop -> 
                                shop.name == post.location?.name || shop.placeId == post.location?.placeId
                            }
                        }

                        // Make sure we log what caption we're using
                        if (coffeeShop != null) {
                            Log.d("FeedFragment", "Found coffee shop: ${coffeeShop.name}, caption: ${coffeeShop.caption}")
                        } else {
                            Log.d("FeedFragment", "Coffee shop not found for ${post.location?.name}")
                        }

                        val bundle = Bundle().apply {
                            putString("name", post.location?.name ?: "Unnamed Coffee Shop")
                            putString("description", coffeeShop?.caption ?: "No description available") 
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
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
        })

        // Add post button
        view.findViewById<FloatingActionButton>(R.id.addPostFab)?.setOnClickListener {
            findNavController().navigate(R.id.action_feedFragment_to_addPostFragment)
        }

        // Set up Swipe to Refresh
        view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)?.setOnRefreshListener {
            viewModel.refreshPosts()
        }

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
            feedAdapter.clearAndAddItems(posts)
            
            // Try to restore scroll position
            if (savedScrollPosition > 0) {
                (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    savedScrollPosition, savedScrollOffset
                )
                savedScrollPosition = 0
                savedScrollOffset = 0
            }
            
            // Always cache posts whenever we get them
            if (posts.isNotEmpty()) {
                Log.d("FeedFragment", "Caching ${posts.size} posts to database")
                viewModel.cachePosts()
            }
            
            // Set up comment count listeners
            setupCommentCountListeners()
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d("FeedFragment", "Loading status changed: $isLoading")
            view?.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)?.isRefreshing = isLoading
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

    fun updateCommentCount(postId: String, count: Int) {
        viewModel.updateCommentCount(postId, count)
    }

    override fun onResume() {
        super.onResume()
        
        // Load posts if we have none yet
        if (::viewModel.isInitialized) {
            // First try to refresh user data to ensure profile images are up to date
            viewModel.refreshUserData()
            
            // Then handle normal post loading
            viewModel.loadCachedPosts()
            
            // Only load from network if we still don't have posts
            if (viewModel.feedPosts.value?.isEmpty() != false) {
                Log.d("FeedFragment", "onResume: No posts in cache, loading from network")
                viewModel.loadInitialPosts()
            }
        }
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
        // Clear comment count listeners when destroying view
        clearCommentListeners()
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
}

