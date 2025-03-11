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
import com.squareup.picasso.Picasso

class FeedAdapter(
    private var feedItems: MutableList<FeedItem>, // Make feedItems mutable
    private val onMoreInfoClick: (FeedItem) -> Unit,
    private val onCommentClick: (FeedItem) -> Unit,
    private val showOptionsMenu: Boolean
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    private val likedStates = mutableMapOf<Int, Boolean>()
    private var postOptionsClickListener: ((View, Int) -> Unit)? = null

    fun setPostOptionsClickListener(listener: (View, Int) -> Unit) {
        postOptionsClickListener = listener
    }

    // Method to add new items (for incremental loading)
    fun addItems(newItems: List<FeedItem>) {
        val startPosition = feedItems.size
        feedItems.addAll(newItems)  // Add new items to the list
        notifyItemRangeInserted(startPosition, newItems.size)  // Notify the adapter of the new items
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

        // Correct ImageView for post photo
        val coffeeImage: ImageView = itemView.findViewById(R.id.coffeeImage)

        fun bind(feedItem: FeedItem) {
            userName.text = feedItem.userName
            locationName.text = feedItem.location?.name
            reviewText.text = feedItem.experienceDescription

            // Load user avatar (profile photo) using Picasso (or Coil, depending on your choice)
            feedItem.userPhotoUrl?.let {
                Picasso.get()
                    .load(it)
                    .into(userAvatar) // Load into ImageView
            }
            // Load image into ImageView for the post photo using Picasso
            feedItem.photoUrl?.let {
                Picasso.get().load(it).into(coffeeImage) // Load post photo into ImageView
            }

            postOptionsButton.visibility = if (showOptionsMenu) View.VISIBLE else View.GONE

            val isLiked = likedStates[adapterPosition] ?: false
            updateLikeButton(likeButton, isLiked)

            likeButton.setOnClickListener {
                val newLikedState = !(likedStates[adapterPosition] ?: false)
                likedStates[adapterPosition] = newLikedState
                updateLikeButton(likeButton, newLikedState)
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
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.feed_post, parent, false)
        return FeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        holder.bind(feedItems[position])
    }

    private fun updateLikeButton(imageView: ImageView, isLiked: Boolean) {
        imageView.setImageResource(
            if (isLiked) R.drawable.ic_heart_filled
            else R.drawable.ic_heart_outline
        )
        imageView.setColorFilter(
            ContextCompat.getColor(imageView.context, R.color.coffee_primary)
        )
    }

    override fun getItemCount() = feedItems.size
}
