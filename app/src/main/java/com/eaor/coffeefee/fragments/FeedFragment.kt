package com.eaor.coffeefee.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.adapters.FeedAdapter
import com.eaor.coffeefee.models.FeedItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FeedFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

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

        // Set up RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Fetch current user ID
        val currentUser = auth.currentUser
        val currentUserId = currentUser?.uid

        db.collection("Posts")
            .get()
            .addOnSuccessListener { result ->
                val feedItems = mutableListOf<FeedItem>()

                for (document in result) {
                    // Map the data from Firestore to FeedItem
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

                    // Fetch user name from "Users" collection based on userId
                    db.collection("Users")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val userName = userDoc.getString("name") ?: "Unknown User"

                            // Create a FeedItem object and add it to the list
                            val feedItem = location?.let {
                                FeedItem(
                                    id = document.id, // Firestore document ID is used as the unique ID
                                    userId = userId,
                                    userName = userName, // Use the fetched user name
                                    experienceDescription = experienceDescription,
                                    location = it,
                                    photoUrl = photoUrl,
                                    timestamp = timestamp
                                )
                            }

                            if (feedItem != null) {
                                feedItems.add(feedItem)

                                // After adding the feed item, update the RecyclerView adapter
                                val adapter = FeedAdapter(
                                    feedItems,
                                    onMoreInfoClick = { feedItem ->
                                        val bundle = Bundle().apply {
                                            putString("landName", feedItem.location?.name ?: "Unknown Location")
                                            putFloat("latitude", feedItem.location?.latitude?.toFloat() ?: 0f)
                                            putFloat("longitude", feedItem.location?.longitude?.toFloat() ?: 0f)
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
                                recyclerView.adapter = adapter
                            }
                        }
                        .addOnFailureListener { exception ->
                            // Handle the error when fetching the user document
                            Log.e("FeedFragment", "Error fetching user name: $exception")
                        }
                }
            }
            .addOnFailureListener { exception ->
                // Handle the error
                Log.e("FeedFragment", "Error fetching posts: $exception")
            }


    }
    }

