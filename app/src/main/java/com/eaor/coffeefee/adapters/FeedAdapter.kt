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
        val position = _feedItems.indexOfFirst { it.id == postId }
        if (position != -1) {
            _feedItems[position].commentCount = count
            notifyItemChanged(position, PAYLOAD_COMMENT_COUNT)
        }
    }

    fun clearAndAddItems(newItems: List<FeedItem>) {
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
                _feedItems[i].userName = userName
                _feedItems[i].userPhotoUrl = userPhotoUrl
                updated = true
                notifyItemChanged(i)
            }
        }
        
        if (updated) {
            Log.d("FeedAdapter", "Updated user data for user $userId in adapter")
        }
    }

    companion object {
        private const val PAYLOAD_COMMENT_COUNT = "payload_comment_count"
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
        val coffeeImage: ImageView = itemView.findViewById(R.id.coffeeImage)

        // Correct ImageView for user photo
        val userPhoto: ImageView = itemView.findViewById(R.id.userAvatar)

        fun bind(feedItem: FeedItem) {
            userName.text = feedItem.userName
            locationName.text = feedItem.location?.name
            reviewText.text = feedItem.experienceDescription

            // Load user profile image using Picasso
            if (!feedItem.userPhotoUrl.isNullOrEmpty()) {
                // Use Log to help debug the image loading
                Log.d("FeedAdapter", "Loading profile image for ${feedItem.userName}: ${feedItem.userPhotoUrl}")
                
                // Use Picasso with no caching to ensure fresh images
                Picasso.get()
                    .load(feedItem.userPhotoUrl)
                    .transform(CircleTransform())
                    .placeholder(R.drawable.default_avatar)
                    .error(R.drawable.default_avatar)
                    .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                    .networkPolicy(NetworkPolicy.NO_CACHE)
                    .into(userPhoto)
            } else {
                userPhoto.setImageResource(R.drawable.default_avatar)
            }

            // Load post photo
            if (!feedItem.photoUrl.isNullOrEmpty()) {
                try {
                    coffeeImage.visibility = View.VISIBLE
                    Picasso.get()
                        .load(feedItem.photoUrl)
                        .placeholder(R.drawable.placeholder)
                        .error(R.drawable.placeholder)
                        .into(coffeeImage)
                } catch (e: Exception) {
                    Log.e("FeedAdapter", "Error loading post image: ${e.message}")
                    coffeeImage.visibility = View.GONE
                }
            } else {
                coffeeImage.visibility = View.GONE
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
        
        // Set user photo with Picasso for consistent handling and no cache
        if (!feedItem.userPhotoUrl.isNullOrEmpty()) {
            Picasso.get()
                .load(feedItem.userPhotoUrl)
                .transform(CircleTransform())
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                .networkPolicy(NetworkPolicy.NO_CACHE)
                .into(holder.userPhoto)
        } else {
            holder.userPhoto.setImageResource(R.drawable.default_avatar)
        }
        
        holder.bind(feedItem)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads[0] == PAYLOAD_COMMENT_COUNT) {
            // Only update the comment count
            holder.commentCount.text = _feedItems[position].commentCount.toString()
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount() = _feedItems.size
}
