package com.eaor.coffeefee.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.FeedItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import android.view.animation.AccelerateDecelerateInterpolator
import android.util.Log
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.eaor.coffeefee.utils.CircleTransform
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.NetworkPolicy
import com.eaor.coffeefee.utils.ImageLoader

class FeedAdapter(
    private val _feedItems: MutableList<FeedItem>, // Rename to _feedItems
    private val onMoreInfoClick: (FeedItem) -> Unit,
    private val onCommentClick: (FeedItem) -> Unit,
    private val showOptionsMenu: Boolean
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    private var postOptionsClickListener: ((View, Int) -> Unit)? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isLikeInProgress = false
    private val rejectedPostIds = mutableSetOf<String>()

    fun setPostOptionsClickListener(listener: (View, Int) -> Unit) {
        postOptionsClickListener = listener
    }

    // Method to add new items (for incremental loading)
    fun addItems(newItems: List<FeedItem>) {
        if (newItems.isEmpty()) return
        
        // REMOVE the duplicate filtering
        val startPos = _feedItems.size
        _feedItems.addAll(newItems)
        
        try {
            notifyItemRangeInserted(startPos, newItems.size)
            Log.d("FeedAdapter", "Added ${newItems.size} items, total: ${_feedItems.size}")
        } catch (e: Exception) {
            // Fall back to notifyDataSetChanged if the range insert fails
            Log.e("FeedAdapter", "Error in incremental update: ${e.message}")
            notifyDataSetChanged()
        }
    }

    fun updateCommentCount(postId: String, count: Int) {
        try {
            Log.d("FeedAdapter", "Updating comment count for post $postId to $count")
            
            val position = _feedItems.indexOfFirst { it.id == postId }
            if (position != -1) {
                val oldCount = _feedItems[position].commentCount
                Log.d("FeedAdapter", "Found post at position $position, updating comment count: $oldCount -> $count")
                
                // Only update and notify if count actually changed
                if (oldCount != count) {
                    // Update the model
                    _feedItems[position].commentCount = count
                    
                    // Make sure we log the update attempt and result
                    Log.d("FeedAdapter", "Comment count changed from $oldCount to $count for post at position $position")
                    
                    try {
                        // Use notifyItemChanged with payload for efficiency
                        notifyItemChanged(position, PAYLOAD_COMMENT_COUNT)
                        Log.d("FeedAdapter", "Successfully notified adapter with payload for position $position")
                    } catch (e: Exception) {
                        Log.e("FeedAdapter", "Error in notifyItemChanged with payload: ${e.message}")
                        
                        // Fallback to regular notify if payload method fails
                        try {
                            notifyItemChanged(position)
                            Log.d("FeedAdapter", "Used fallback full notify for position $position")
                        } catch (e2: Exception) {
                            Log.e("FeedAdapter", "Full notifyItemChanged also failed: ${e2.message}")
                            
                            // Last resort - notify data set changed
                            try {
                                notifyDataSetChanged()
                                Log.d("FeedAdapter", "Used notifyDataSetChanged as last resort")
                            } catch (e3: Exception) {
                                Log.e("FeedAdapter", "All notification methods failed: ${e3.message}")
                            }
                        }
                    }
                } else {
                    Log.d("FeedAdapter", "Comment count unchanged (still $count), skipping update")
                }
            } else {
                Log.e("FeedAdapter", "Could not find post $postId in feed items list (size: ${_feedItems.size})")
                
                // Debug output of all post IDs to help diagnose issues
                _feedItems.forEachIndexed { index, item ->
                    Log.d("FeedAdapter", "Item[$index]: id=${item.id}, commentCount=${item.commentCount}")
                }
            }
        } catch (e: Exception) {
            Log.e("FeedAdapter", "Error updating comment count: ${e.message}", e)
        }
    }

    fun clearAndAddItems(newItems: List<FeedItem>) {
        // Skip operation if the new items are identical to existing ones
        if (newItems == _feedItems) {
            Log.d("FeedAdapter", "New items identical to existing ones, skipping update")
            return
        }
        
        // Check if we just need to append new items
        if (_feedItems.isEmpty()) {
            // Empty list - just add all items
            _feedItems.addAll(newItems)
            notifyDataSetChanged()
            Log.d("FeedAdapter", "Added ${newItems.size} items to empty adapter")
            return
        }
        
        // Check if we're adding the same items in the same order
        if (newItems.size > _feedItems.size && 
            newItems.subList(0, _feedItems.size) == _feedItems) {
            // New items contain all existing items and then some more
            val startPos = _feedItems.size
            val addedItems = newItems.subList(startPos, newItems.size)
            _feedItems.addAll(addedItems)
            notifyItemRangeInserted(startPos, addedItems.size)
            Log.d("FeedAdapter", "Appended ${addedItems.size} new items")
            return
        }
        
        // Otherwise do a full replacement
        _feedItems.clear()
        _feedItems.addAll(newItems)
        notifyDataSetChanged()
        Log.d("FeedAdapter", "Cleared and added ${newItems.size} items")
    }

    fun updateItems(newItems: List<FeedItem>) {
        _feedItems.clear()
        _feedItems.addAll(newItems)
        
        try {
            notifyDataSetChanged()
            Log.d("FeedAdapter", "Updated with ${newItems.size} items")
        } catch (e: Exception) {
            Log.e("FeedAdapter", "Error updating items: ${e.message}")
        }
    }

    // Method to get the current feed items
    val feedItems: List<FeedItem>
        get() = _feedItems.toList()

    // Add this new method for clearing adapter data
    fun clearData() {
        _feedItems.clear()
        rejectedPostIds.clear() // Also clear the rejected posts set
        notifyDataSetChanged()
        Log.d("FeedAdapter", "Cleared all data from adapter")
    }

    fun getItems(): List<FeedItem> {
        return _feedItems.toList()
    }

    // Method to update user data in all posts
    fun updateUserData(userId: String, userName: String, userPhotoUrl: String?) {
        var updated = false
        
        // Update all posts by this user
        for (i in _feedItems.indices) {
            if (_feedItems[i].userId == userId) {
                Log.d("FeedAdapter", "Updating post at position $i for user $userId")
                
                // Update the post data
                _feedItems[i].userName = userName
                _feedItems[i].userPhotoUrl = userPhotoUrl
                updated = true
                
                // Clear Picasso's cache for this URL if available
                if (!userPhotoUrl.isNullOrEmpty()) {
                    try {
                        // This forces Picasso to invalidate its cache for this URL
                        Picasso.get().invalidate(userPhotoUrl)
                    } catch (e: Exception) {
                        Log.e("FeedAdapter", "Error invalidating Picasso cache: ${e.message}")
                    }
                }
                
                // Notify that this specific item changed
                notifyItemChanged(i)
            }
        }
        
        if (updated) {
            Log.d("FeedAdapter", "Updated user data for user $userId in adapter")
        } else {
            Log.d("FeedAdapter", "No posts found for user $userId in adapter")
        }
    }

    fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            // Keep items but add a loading indicator if needed
            // Or just indicate visually that we're refreshing
            try {
                // Use existing data but notify adapter that we're "refreshing"
                notifyItemRangeChanged(0, _feedItems.size, "LOADING")
                Log.d("FeedAdapter", "Showing loading state for ${_feedItems.size} items")
            } catch (e: Exception) {
                Log.e("FeedAdapter", "Error showing loading: ${e.message}")
            }
        } else {
            // Remove loading indicator if needed
            notifyDataSetChanged()
        }
    }

    /**
     * Refreshes all user data and images in the feed for the specified user
     * @param userId The user ID to refresh data for
     * @param userName The new user name
     * @param userPhotoUrl The new user photo URL
     */
    fun refreshUserData(userId: String, userName: String, userPhotoUrl: String?) {
        Log.d("FeedAdapter", "Refreshing all user data for $userId with name $userName")
        
        var updated = false
        var userPostCount = 0
        
        // Update all posts by this user
        for (i in _feedItems.indices) {
            if (_feedItems[i].userId == userId) {
                val post = _feedItems[i]
                
                // Check if data actually changed
                val dataChanged = post.userName != userName || post.userPhotoUrl != userPhotoUrl
                
                if (dataChanged) {
                    // Update the post user data
                    post.userName = userName
                    post.userPhotoUrl = userPhotoUrl
                    updated = true
                    
                    // Clear image cache if user photo URL changed
                    if (post.userPhotoUrl != userPhotoUrl && !userPhotoUrl.isNullOrEmpty()) {
                        try {
                            Picasso.get().invalidate(userPhotoUrl)
                        } catch (e: Exception) {
                            Log.e("FeedAdapter", "Error invalidating Picasso cache: ${e.message}")
                        }
                    }
                }
                
                userPostCount++
            }
        }
        
        // Only notify data set change if we actually made changes
        if (updated) {
            notifyDataSetChanged()
            Log.d("FeedAdapter", "Updated ${userPostCount} posts for user ${userId}")
        } else if (userPostCount > 0) {
            Log.d("FeedAdapter", "Found ${userPostCount} posts for user ${userId} but no data changed")
        } else {
            Log.d("FeedAdapter", "No posts found for user ${userId}")
        }
    }

    companion object {
        private const val PAYLOAD_COMMENT_COUNT = "payload_comment_count"
        const val COMMENT_COUNT = "COMMENT_COUNT"
    }

    inner class FeedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userAvatar: ImageView = itemView.findViewById(R.id.userAvatar) // ImageView for the user's profile photo
        val userName: TextView = itemView.findViewById(R.id.userName)
        val locationName: TextView = itemView.findViewById(R.id.locationName)
        val reviewText: TextView = itemView.findViewById(R.id.reviewText)
        val moreInfoButton: TextView = itemView.findViewById(R.id.moreInfoButton)
        val likeButton: ImageView = itemView.findViewById(R.id.likeButton)
        val postOptionsButton: ImageButton = itemView.findViewById(R.id.postOptionsButton)
        val commentsButton: ImageButton = itemView.findViewById(R.id.commentsButton)
        val commentCount: TextView = itemView.findViewById(R.id.commentCount)
        val likeCount: TextView = itemView.findViewById(R.id.likeCount)

        // Correct ImageView for post photo
        val postImage: ImageView = itemView.findViewById(R.id.coffeeImage)

        // Correct ImageView for user photo
        val userPhoto: ImageView = itemView.findViewById(R.id.userAvatar)

        fun bind(feedItem: FeedItem) {
            // Ensure we have a valid username to display
            val displayName = if (feedItem.userName.isNullOrEmpty()) {
                "Unknown User"
            } else {
                feedItem.userName
            }
            
            userName.text = displayName
            locationName.text = feedItem.location?.name
            reviewText.text = feedItem.experienceDescription

            // Load user avatar with improved caching settings
            ImageLoader.loadProfileImage(userAvatar, feedItem.userPhotoUrl)
            
            // Load post photo
            if (!feedItem.photoUrl.isNullOrEmpty()) {
                ImageLoader.loadPostImage(postImage, feedItem.photoUrl)
                postImage.visibility = View.VISIBLE
            } else {
                postImage.visibility = View.GONE
            }

            postOptionsButton.visibility = if (showOptionsMenu) View.VISIBLE else View.GONE

            // Set the initial like state based on the current user's like status
            val userId = auth.currentUser?.uid
            val isCurrentlyLiked = feedItem.isLikedByCurrentUser || (userId != null && feedItem.likes.contains(userId))
            setInitialLikeState(isCurrentlyLiked)
            likeCount.text = feedItem.likeCount.toString()

            likeButton.setOnClickListener {
                if (isLikeInProgress) return@setOnClickListener
                val userId = auth.currentUser?.uid ?: return@setOnClickListener

                isLikeInProgress = true

                // Toggle like state
                val isCurrentlyLiked = feedItem.isLikedByCurrentUser
                feedItem.isLikedByCurrentUser = !isCurrentlyLiked

                // Update like count and likes list
                if (isCurrentlyLiked) {
                    feedItem.likeCount -= 1
                    feedItem.likes = feedItem.likes.filter { it != userId } // Remove user ID from likes
                } else {
                    feedItem.likeCount += 1
                    if (!feedItem.likes.contains(userId)) {
                        feedItem.likes = feedItem.likes + userId // Add user ID to likes
                    }
                }

                likeCount.text = feedItem.likeCount.toString()
                animateLikeButton(!isCurrentlyLiked)

                val postRef = db.collection("Posts").document(feedItem.id)

                db.runTransaction { transaction ->
                    val post = transaction.get(postRef)
                    val likes = post.get("likes") as? List<String> ?: listOf()

                    // Update likes list in Firestore
                    if (isCurrentlyLiked) {
                        transaction.update(postRef,
                            "likes", likes.filter { it != userId },
                            "likeCount", FieldValue.increment(-1)
                        )
                    } else {
                        transaction.update(postRef,
                            "likes", likes + userId,
                            "likeCount", FieldValue.increment(1)
                        )
                    }
                }.addOnSuccessListener {
                    // Transaction successful, no need to revert UI changes
                    isLikeInProgress = false
                }.addOnFailureListener {
                    // Revert UI changes if the operation failed
                    feedItem.isLikedByCurrentUser = isCurrentlyLiked
                    feedItem.likeCount += if (isCurrentlyLiked) 1 else -1
                    likeCount.text = feedItem.likeCount.toString()
                    animateLikeButton(isCurrentlyLiked)
                    isLikeInProgress = false
                }
            }

            moreInfoButton.setOnClickListener {
                onMoreInfoClick(feedItem)
            }

            postOptionsButton.setOnClickListener { view ->
                postOptionsClickListener?.invoke(view, adapterPosition)
            }

            commentsButton.setOnClickListener {
                onCommentClick(feedItem)
            }

            // Update comment count
            commentCount.text = feedItem.commentCount.toString()
        }

        private fun setInitialLikeState(isLiked: Boolean) {
            // Set the initial state without animation
            likeButton.setImageResource(
                if (isLiked) R.drawable.ic_heart_filled
                else R.drawable.ic_heart_outline
            )
            likeButton.setColorFilter(
                ContextCompat.getColor(likeButton.context, R.color.coffee_primary)
            )
        }

        private fun animateLikeButton(isLiked: Boolean) {
            // Cancel any ongoing animations first to make it interruptible
            likeButton.animate().cancel()
            
            // Change the image resource immediately
            likeButton.setImageResource(
                if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            
            // Use much shorter durations for quicker feedback
            likeButton.animate()
                .scaleX(1.2f) // Slightly larger scale for more noticeable effect
                .scaleY(1.2f)
                .setDuration(50) // Shorter duration (50ms instead of 100ms)
                .setInterpolator(AccelerateInterpolator()) // Faster acceleration
                .withEndAction {
                    likeButton.animate()
                        .scaleX(1f) // Back to original size
                        .scaleY(1f)
                        .setDuration(50) // Shorter return duration
                        .setInterpolator(DecelerateInterpolator()) // Smoother deceleration
                        .start()
                }
                .start()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.feed_post, parent, false)
        return FeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val feedItem = _feedItems[position]
        
        // Don't double load user photo here - it's already handled in the bind method
        // which is called right after this
        
        holder.bind(feedItem)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            // Handle our payload types
            when {
                payloads.contains(PAYLOAD_COMMENT_COUNT) || payloads.contains("COMMENT_COUNT") -> {
                    // Only update the comment count text
                    try {
                        val count = _feedItems[position].commentCount
                        Log.d("FeedAdapter", "Binding comment count update: position=$position, count=$count")
                        holder.commentCount.text = count.toString()
                    } catch (e: Exception) {
                        Log.e("FeedAdapter", "Error updating comment count in ViewHolder: ${e.message}")
                        // Fallback to full rebind
                        super.onBindViewHolder(holder, position, payloads)
                    }
                }
                else -> {
                    // For other payload types, do a full rebind
                    super.onBindViewHolder(holder, position, payloads)
                }
            }
        } else {
            // No payloads, do a full rebind
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount() = _feedItems.size
}
