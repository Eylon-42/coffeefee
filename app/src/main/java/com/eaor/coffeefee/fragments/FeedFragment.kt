package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.FeedAdapter
import com.eaor.coffeefee.models.FeedItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
class FeedFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var lastVisible: DocumentSnapshot // To keep track of the last loaded document for pagination
    private var isLoading = false // To prevent multiple simultaneous requests

    private val pageSize = 4 // Number of posts to load initially
    private val incrementalLoadSize = 1 // Load 1 post after initial load

    private lateinit var recyclerView: RecyclerView
    private lateinit var feedAdapter: FeedAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Set up toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        view.findViewById<TextView>(R.id.toolbarTitle).text = "Feed"

        // Hide back button as this is the main feed screen
        view.findViewById<ImageButton>(R.id.backButton).visibility = View.GONE

        // Set up RecyclerView
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initial posts load
        loadPosts(incremental = false)

        // Add scroll listener to implement lazy loading
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Check if the user has scrolled to the bottom
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

                // If we are at the bottom and not already loading more posts, load the next batch
                if (!isLoading && lastVisiblePosition == recyclerView.adapter!!.itemCount - 1) {
                    loadPosts(incremental = true)
                }
            }
        })
    }

    private fun loadPosts(incremental: Boolean) {
        if (isLoading) return // Prevent fetching more posts while a request is in progress
        isLoading = true

        // Start the query with a limit of posts
        var query = db.collection("Posts")
            .limit(if (incremental) incrementalLoadSize.toLong() else pageSize.toLong())

        // If we have a last visible document, use startAfter for pagination
        if (::lastVisible.isInitialized && incremental) {
            query = query.startAfter(lastVisible)
        }

        // Fetch the posts
        query.get()
            .addOnSuccessListener { result ->
                val feedItems = mutableListOf<FeedItem>()
                for (document in result) {
                    val userId = document.getString("UserId") ?: ""
                    val experienceDescription = document.getString("experienceDescription") ?: ""

                    // Get location data from Firestore document
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

                    val photoUrl = document.getString("photoUrl")
                    val timestamp = document.getLong("timestamp") ?: 0L

                    val tempFeedItem = location?.let {
                        FeedItem(
                            id = document.id,
                            userId = userId,
                            userName = "", // Placeholder for now, we'll update later
                            experienceDescription = experienceDescription,
                            location = it,
                            photoUrl = photoUrl,
                            timestamp = timestamp,
                            userPhotoUrl = null // Placeholder for now
                        )
                    }

                    if (tempFeedItem != null) {
                        feedItems.add(tempFeedItem)
                    }
                }

                // Store the last document fetched for pagination
                if (result.documents.isNotEmpty()) {
                    lastVisible = result.documents[result.size() - 1]
                }

                // Now that we have all posts, let's fetch user information for each post
                fetchUserInfo(feedItems, incremental)
            }
            .addOnFailureListener { exception ->
                Log.e("FeedFragment", "Error fetching posts: $exception")
                isLoading = false
            }
    }

    private fun fetchUserInfo(feedItems: MutableList<FeedItem>, incremental: Boolean) {
        val updatedFeedItems = mutableListOf<FeedItem>()
        var processedCount = 0

        // Fetch user information for each post
        feedItems.forEach { feedItem ->
            val userId = feedItem.userId
            db.collection("Users")
                .document(userId)
                .get()
                .addOnSuccessListener { userDoc ->
                    val userName = userDoc.getString("name") ?: "Unknown User"
                    val userPhotoUrl = userDoc.getString("profilePhotoUrl") // This is the gs:// URL

                    // Fetch the download URL from Firebase Storage for the user's profile photo
                    val storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(userPhotoUrl ?: "")
                    storageReference.downloadUrl.addOnSuccessListener { uri ->
                        // Convert gs:// URL to https:// URL
                        val httpsUrl = uri.toString()

                        // Update the FeedItem with the fetched user info
                        val updatedFeedItem = feedItem.copy(
                            userName = userName,
                            userPhotoUrl = httpsUrl // Use the https URL for the profile photo
                        )

                        // Add the updated FeedItem to the list
                        updatedFeedItems.add(updatedFeedItem)

                        // Once all items are processed, update the RecyclerView adapter
                        processedCount++
                        if (processedCount == feedItems.size) {
                            if (!incremental) {
                                // Initialize the adapter only once during the initial load
                                feedAdapter = FeedAdapter(
                                    updatedFeedItems,
                                    onMoreInfoClick = { feedItem ->
                                        val bundle = Bundle().apply {
                                            putString("description", feedItem.experienceDescription ?: "Unknown Location")
                                            putString("name", feedItem.location?.name ?: "Unknown Location")
                                            putFloat("latitude", feedItem.location?.latitude?.toFloat() ?: 0f)
                                            putFloat("longitude", feedItem.location?.longitude?.toFloat() ?: 0f)
                                            putString("imageUrl", feedItem.photoUrl) // Pass the photo URL here

                                        }
                                        findNavController().navigate(R.id.action_feedFragment_to_coffeeFragment, bundle)
                                    },
                                    onCommentClick = { feedItem ->
                                        val bundle = Bundle().apply {
                                            putString("postId", feedItem.id) // Pass the postId for comments
                                        }
                                        findNavController().navigate(R.id.action_feedFragment_to_commentsFragment, bundle)
                                    },
                                    showOptionsMenu = false
                                )
                                recyclerView.adapter = feedAdapter
                            } else {
                                // Append the new posts to the existing adapter
                                feedAdapter.addItems(updatedFeedItems)
                            }
                        }
                    }
                        .addOnFailureListener { exception ->
                            Log.e("FeedFragment", "Error fetching user photo URL: $exception")
                        }
                }
                .addOnFailureListener { exception ->
                    Log.e("FeedFragment", "Error fetching user info: $exception")
                }
        }

        // Set isLoading to false once we have finished loading posts
        isLoading = false
    }
}
