package com.eaor.coffeefee.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.FeedItem

class FeedAdapter(
    private val feedItems: List<FeedItem>,
    private val onMoreInfoClick: (FeedItem) -> Unit
) : RecyclerView.Adapter<FeedAdapter.ViewHolder>() {

    private val likedStates = mutableMapOf<Int, Boolean>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userName: TextView = view.findViewById(R.id.userName)
        val userDescription: TextView = view.findViewById(R.id.userDescription)
        val reviewText: TextView = view.findViewById(R.id.reviewText)
        val moreInfoButton: TextView = view.findViewById(R.id.moreInfoButton)
        val likeButton: ImageView = view.findViewById(R.id.likeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.feed_post, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val feedItem = feedItems[position]
        
        holder.userName.text = feedItem.userName
        holder.userDescription.text = "Visited ${feedItem.coffeeShop.name}"
        holder.reviewText.text = feedItem.reviewText
        
        // Set initial like button state
        val isLiked = likedStates[position] ?: false
        updateLikeButton(holder.likeButton, isLiked)
        
        holder.likeButton.setOnClickListener {
            val newLikedState = !(likedStates[position] ?: false)
            likedStates[position] = newLikedState
            updateLikeButton(holder.likeButton, newLikedState)
        }
        
        holder.moreInfoButton.setOnClickListener {
            onMoreInfoClick(feedItem)
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