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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.FeedAdapter
import com.eaor.coffeefee.database.PostDatabaseHelper
import com.eaor.coffeefee.models.FeedItem
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
    private var lastVisible: DocumentSnapshot? = null
    private var isLoading = false
    private val pageSize = 6 // Initial load size
    private val moreSize = 4 // Update from 2 to 4 posts for each subsequent load
    private lateinit var recyclerView: RecyclerView
    private lateinit var feedAdapter: FeedAdapter
    private lateinit var postDatabaseHelper: PostDatabaseHelper
    private val loadedPostIds = HashSet<String>() // Track which posts we've already seen
    private var savedScrollPosition = 0
    private var savedScrollOffset = 0

    // Change the type of commentListeners from function to ListenerRegistration
    private val commentListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Get the view 
        val view = inflater.inflate(R.layout.fragment_feed, container, false)
        
        // Try to find RecyclerView here, and log if not found
        val foundRecyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        if (foundRecyclerView == null) {
            Log.e("FeedFragment", "RecyclerView not found in the layout during onCreateView")
        }
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize PostDatabaseHelper
        postDatabaseHelper = PostDatabaseHelper(requireContext())

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
            feedAdapter = FeedAdapter(mutableListOf(), onMoreInfoClick = { post ->
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
            }, onCommentClick = { feedItem ->
                // Save scroll position before navigating
                saveScrollPosition()
                
                val bundle = Bundle().apply { putString("postId", feedItem.id) }
                findNavController().navigate(R.id.action_feedFragment_to_commentsFragment, bundle)
            }, showOptionsMenu = false)
            
            // Populate loadedPostIds with what's already in the adapter
            loadedPostIds.addAll(feedAdapter.feedItems.map { it.id })
        }
        
        recyclerView.adapter = feedAdapter

        // Set up FloatingActionButton
        view.findViewById<FloatingActionButton>(R.id.addPostFab).setOnClickListener {
            // Save scroll position before navigating
            saveScrollPosition()
            
            findNavController().navigate(R.id.action_feedFragment_to_addPostFragment)
        }

        // Set up pagination
        setupPagination()
        
        // Initial load only if needed
        if (feedAdapter.feedItems.isEmpty()) {
            loadInitialPosts()
        } else {
            // Restore the saved scroll position
            restoreScrollPosition()
        }
    }

    private fun setupPagination() {
        recyclerView.clearOnScrollListeners()
        
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Skip if already loading or not scrolling down
                if (isLoading || dy <= 0) return
                
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount
                
                // Only load more when user has reached the actual bottom
                // Previously triggered when within 3 items of the bottom
                if (lastVisibleItemPosition == totalItemCount - 1) {
                    Log.d("FeedFragment", "🔄 Reached the bottom (position $lastVisibleItemPosition of $totalItemCount) - loading more posts")
                    loadMorePosts()
                }
            }
        })
    }

    private fun loadInitialPosts() {
        if (isLoading) {
            Log.d("FeedFragment", "Already loading posts")
            return
        }
        
        Log.d("FeedFragment", "Loading initial posts")
        
        // Reset tracking variables completely
        isLoading = true
        lastVisible = null
        loadedPostIds.clear() // Only clear the set if we're doing a full reload
        
        // Clear the adapter
        feedAdapter.clearData()
        
        // Skip the cache completely - load fresh from network always
        // This ensures we have complete data for each post
        db.collection("Posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Log.d("FeedFragment", "No posts available")
                    isLoading = false
                    return@addOnSuccessListener
                }
                
                // Set cursor for pagination - make sure to get the very last document
                lastVisible = snapshot.documents.lastOrNull()
                Log.d("FeedFragment", "Setting cursor to: ${lastVisible?.id}")
                
                // Find unique posts (ignore duplicates)
                val initialPosts = mutableListOf<FeedItem>()
                for (document in snapshot.documents) {
                    val postId = document.id
                    loadedPostIds.add(postId) // Track this post ID
                    initialPosts.add(createFeedItem(document))
                }
                
                Log.d("FeedFragment", "Found ${initialPosts.size} new posts from network")
                
                // Process the posts
                processAndUpdatePosts(initialPosts, false) // false = replace all
            }
            .addOnFailureListener { e ->
                Log.e("FeedFragment", "Error loading initial posts: ${e.message}")
                isLoading = false
            }
    }

    private fun loadMorePosts() {
        if (isLoading) {
            Log.d("FeedFragment", "⏳ Already loading more posts")
            return
        }
        
        if (lastVisible == null) {
            Log.d("FeedFragment", "❌ No cursor available for pagination")
            return
        }
        
        isLoading = true
        val lastVisibleId = lastVisible?.id ?: "unknown"
        Log.d("FeedFragment", "📱 Loading more posts after: $lastVisibleId")
        
        // Use a larger batch size to ensure we find enough new posts
        val querySize = 20 // Increased for better chance of finding enough unique posts
        
        db.collection("Posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .startAfter(lastVisible)
            .limit(querySize.toLong())
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Log.d("FeedFragment", "No more posts available")
                    isLoading = false
                    return@addOnSuccessListener
                }
                
                // Always move the cursor to the last document in the batch
                val oldCursor = lastVisible
                lastVisible = snapshot.documents.lastOrNull()
                
                // Find unique posts (ignore duplicates)
                val newPosts = mutableListOf<FeedItem>()
                for (document in snapshot.documents) {
                    val postId = document.id
                    if (!loadedPostIds.contains(postId)) {
                        loadedPostIds.add(postId)
                        newPosts.add(createFeedItem(document))
                        
                        // Take exactly moreSize (4) new posts
                        if (newPosts.size >= moreSize) {
                            break
                        }
                    }
                }
                
                if (newPosts.isEmpty()) {
                    // If no new posts found, try with timestamp approach
                    Log.d("FeedFragment", "⚠️ No new posts found in this batch, trying timestamp approach")
                    isLoading = false
                    
                    val oldTimestamp = oldCursor?.getLong("timestamp") ?: Long.MAX_VALUE
                    
                    db.collection("Posts")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .whereLessThan("timestamp", oldTimestamp)
                        .limit(querySize.toLong())
                        .get()
                        .addOnSuccessListener { tsSnapshot ->
                            if (!tsSnapshot.isEmpty) {
                                lastVisible = tsSnapshot.documents.lastOrNull()
                                Log.d("FeedFragment", "Found posts by timestamp, new cursor: ${lastVisible?.id}")
                                
                                // Try loading more with this new cursor
                                Handler(Looper.getMainLooper()).postDelayed({
                                    loadMorePosts()
                                }, 300)
                            } else {
                                Log.d("FeedFragment", "No more posts available by timestamp either")
                            }
                        }
                    
                    return@addOnSuccessListener
                }
                
                Log.d("FeedFragment", "✅ Found ${newPosts.size} new unique posts")
                
                // Process the posts - all new posts will be added
                processAndUpdatePosts(newPosts, true)
            }
            .addOnFailureListener { e ->
                Log.e("FeedFragment", "❌ Error loading more posts: ${e.message}")
                isLoading = false
            }
    }

    // Helper method to create a FeedItem from a document
    private fun createFeedItem(document: DocumentSnapshot): FeedItem {
        // Check for both "UserId" and "userId" to handle existing data
        val userId = document.getString("UserId") ?: document.getString("userId") ?: ""
        
        // Debug log to check the userId
        if (userId.isBlank()) {
            Log.e("FeedFragment", "Empty UserId for post ${document.id}, raw data: ${document.data}")
        } else {
            Log.d("FeedFragment", "Post ${document.id} has UserId: $userId")
        }
        
        val experienceDescription = document.getString("experienceDescription") ?: ""
        
        // Parse location
        val locationMap = document.get("location") as? Map<String, Any>
        val location = if (locationMap != null) {
            FeedItem.Location(
                name = locationMap["name"] as? String ?: "",
                latitude = (locationMap["latitude"] as? Double) ?: 0.0,
                longitude = (locationMap["longitude"] as? Double) ?: 0.0,
                placeId = locationMap["placeId"] as? String
            )
        } else null
        
        // Create feed item
        return FeedItem(
            id = document.id,
            userId = userId,
            userName = "", // Will be filled later
            experienceDescription = experienceDescription,
            location = location,
            photoUrl = document.getString("photoUrl"),
            timestamp = document.getLong("timestamp") ?: 0L,
            userPhotoUrl = document.getString("userPhotoUrl"), // Try getting directly from post first
            likeCount = document.getLong("likeCount")?.toInt() ?: 0,
            commentCount = document.getLong("commentCount")?.toInt() ?: 0,
            likes = document.get("likes") as? List<String> ?: listOf()
        )
    }

    // Convert Firebase Storage URL to a real HTTP URL
    private fun getDownloadUrl(gsUrl: String, callback: (String?) -> Unit) {
        try {
            val storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(gsUrl)
            storageReference.downloadUrl
                .addOnSuccessListener { uri ->
                    Log.d("FeedFragment", "✅ Converted Storage URL to: ${uri}")
                    callback(uri.toString())
                }
                .addOnFailureListener { e ->
                    Log.e("FeedFragment", "❌ Error getting download URL: ${e.message}")
                    callback(null)
                }
        } catch (e: Exception) {
            Log.e("FeedFragment", "❌ Error with storage reference: ${e.message}")
            callback(null)
        }
    }

    // Fix the setupCommentCountListeners method
    private fun setupCommentCountListeners() {
        // Clear any existing listeners first
        clearCommentListeners()
        
        // Set up listeners for each post in the feed
        for (post in feedAdapter.feedItems) {
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

    // Add calls to setup/clear listeners
    private fun processAndUpdatePosts(posts: List<FeedItem>, incremental: Boolean) {
        if (posts.isEmpty()) {
            isLoading = false
            return
        }
        
        val processedPosts = mutableListOf<FeedItem>()
        val pendingCount = AtomicInteger(posts.size)
        
        // What to do when all posts are processed
        val checkCompletion = {
            if (pendingCount.decrementAndGet() == 0) {
                // All posts processed, update UI on main thread to avoid crashes
                Handler(Looper.getMainLooper()).post {
                    if (isAdded && ::feedAdapter.isInitialized) {
                        if (incremental) {
                            // Don't limit incremental posts - add all we processed
                            feedAdapter.addItems(processedPosts)
                            Log.d("FeedFragment", "Added ${processedPosts.size} more posts")
                        } else {
                            // Don't limit initial posts either - show all we loaded
                            feedAdapter.clearAndAddItems(processedPosts)
                            Log.d("FeedFragment", "Replaced with ${processedPosts.size} posts")
                        }
                        
                        // Set up real-time comment count listeners for all posts
                        setupCommentCountListeners()
                    }
                    
                    isLoading = false
                }
            }
        }
        
        // Process each post
        for (post in posts) {
            val userId = post.userId
            
            // Check if userId is valid before proceeding
            if (userId.isBlank()) {
                Log.e("FeedFragment", "Empty userId found for post: ${post.id}")
                val updatedPost = post.copy(userName = "Unknown User")
                processedPosts.add(updatedPost)
                checkCompletion()
                continue // Skip this post and move to the next one
            }
            
            // Get user info from Firestore
            db.collection("Users").document(userId).get()
                .addOnSuccessListener { userDoc ->
                    val userName = userDoc.getString("name") ?: "Unknown User"
                    
                    // Get profile photo URL
                    val userPhotoUrl = userDoc.getString("profilePhotoUrl")
                    
                    Log.d("FeedFragment", "User ${userName} photo URL: ${userPhotoUrl ?: "null"}")
                    
                    // Handle Firebase Storage URLs (gs://) by converting to HTTP URLs
                    if (userPhotoUrl != null && userPhotoUrl.startsWith("gs://")) {
                        getDownloadUrl(userPhotoUrl) { httpUrl ->
                            // Always run on main thread when updating UI
                            Handler(Looper.getMainLooper()).post {
                                if (isAdded) { // Make sure fragment is still attached
                                    val updatedPost = post.copy(
                                        userName = userName,
                                        userPhotoUrl = httpUrl
                                    )
                                    processedPosts.add(updatedPost)
                                    checkCompletion()
                                }
                            }
                        }
                    } else {
                        // Use the URL directly if it's already an HTTP URL or null
                        val updatedPost = post.copy(
                            userName = userName,
                            userPhotoUrl = userPhotoUrl
                        )
                        processedPosts.add(updatedPost)
                        checkCompletion()
                    }
                }
                .addOnFailureListener { e ->
                    // Log the specific error
                    Log.e("FeedFragment", "Failed to get user data for userId=$userId: ${e.message}")
                    // Just use the post with unknown user if we can't get user info
                    val updatedPost = post.copy(userName = "Unknown User")
                    processedPosts.add(updatedPost)
                    checkCompletion()
                }
        }
    }

    fun updateCommentCount(postId: String, count: Int) {
        if (::feedAdapter.isInitialized) {
            feedAdapter.updateCommentCount(postId, count)
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Only reload if we have no posts at all
        if ((!::feedAdapter.isInitialized || feedAdapter.feedItems.isEmpty()) && !isLoading) {
            loadInitialPosts()
        } else {
            // Already have posts, keep the existing paged content
            Log.d("FeedFragment", "Already have ${feedAdapter.feedItems.size} posts, maintaining paged state")
            
            // Make sure we have a valid cursor for pagination if we need to load more
            if (lastVisible == null && feedAdapter.feedItems.isNotEmpty()) {
                // Try to recover pagination state by getting the timestamp of the last visible post
                val lastPost = feedAdapter.feedItems.lastOrNull()
                if (lastPost != null) {
                    Log.d("FeedFragment", "Recovering pagination state for post ID: ${lastPost.id}")
                    
                    // Find the Firestore document for the last post to set as cursor
                    db.collection("Posts")
                        .document(lastPost.id)
                        .get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                lastVisible = document
                                Log.d("FeedFragment", "Pagination cursor recovered")
                            }
                        }
                }
            }
            
            // Set up comment count listeners again when resuming
            setupCommentCountListeners()
            
            // Restore the scroll position when coming back to the fragment
            restoreScrollPosition()
        }
    }

    override fun onPause() {
        super.onPause()
        // Clear comment count listeners when pausing to avoid memory leaks
        clearCommentListeners()
        // Save scroll position when leaving the fragment
        saveScrollPosition()
    }

    // Save current scroll position
    private fun saveScrollPosition() {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        savedScrollPosition = layoutManager.findFirstVisibleItemPosition()
        
        // Also save offset for smooth positioning
        val firstView = recyclerView.getChildAt(0)
        savedScrollOffset = if (firstView != null) firstView.top else 0
    }

    // Restore saved scroll position
    private fun restoreScrollPosition() {
        if (savedScrollPosition > 0) {
            Handler(Looper.getMainLooper()).post {
                (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    savedScrollPosition, 
                    savedScrollOffset
                )
                Log.d("FeedFragment", "Restored scroll to position $savedScrollPosition with offset $savedScrollOffset")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear comment count listeners when destroying view
        clearCommentListeners()
        if (::postDatabaseHelper.isInitialized) {
            postDatabaseHelper.close()
        }
    }
}

