package com.eaor.coffeefee.adapters

import android.content.Context
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

class FeedAdapter(
    private val feedItems: List<FeedItem>,
    private val onMoreInfoClick: (FeedItem) -> Unit,
    private val showOptionsMenu: Boolean = false
) : RecyclerView.Adapter<FeedAdapter.ViewHolder>() {

    private val likedStates = mutableMapOf<Int, Boolean>()
    private var postOptionsClickListener: ((View, Int) -> Unit)? = null

    fun setPostOptionsClickListener(listener: (View, Int) -> Unit) {
        postOptionsClickListener = listener
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userName: TextView = view.findViewById(R.id.userName)
        val userDescription: TextView = view.findViewById(R.id.userDescription)
        val reviewText: TextView = view.findViewById(R.id.reviewText)
        val moreInfoButton: TextView = view.findViewById(R.id.moreInfoButton)
        val likeButton: ImageView = view.findViewById(R.id.likeButton)
        val postOptionsButton: ImageButton = view.findViewById(R.id.postOptionsButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.feed_post, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val feedItem = feedItems[position]
        
        holder.userName.text = feedItem.userName
        holder.userDescription.text = feedItem.userDescription
        holder.reviewText.text = feedItem.reviewText
        
        // Show/hide options menu based on parameter
        holder.postOptionsButton.visibility = if (showOptionsMenu) View.VISIBLE else View.GONE
        
        // Set initial like button state
        val isLiked = likedStates[position] ?: false
        updateLikeButton(holder.likeButton, isLiked)
        
        // Like button click listener
        holder.likeButton.setOnClickListener {
            val newLikedState = !(likedStates[position] ?: false)
            likedStates[position] = newLikedState
            updateLikeButton(holder.likeButton, newLikedState)
        }
        
        // More info button click listener
        holder.moreInfoButton.setOnClickListener {
            onMoreInfoClick(feedItem)
        }

        // Post options button click listener
        holder.postOptionsButton.setOnClickListener { view ->
            postOptionsClickListener?.invoke(view, position)
        }
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