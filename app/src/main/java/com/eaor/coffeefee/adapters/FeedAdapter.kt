package com.eaor.coffeefee.adapters

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.FeedItem

class FeedAdapter(
    private val feedItems: List<FeedItem>,
    private val onMoreInfoClick: (FeedItem) -> Unit,
    private val onCommentClick: (FeedItem) -> Unit,
    private val showOptionsMenu: Boolean
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    private val likedStates = mutableMapOf<Int, Boolean>()
    private var postOptionsClickListener: ((View, Int) -> Unit)? = null

    fun setPostOptionsClickListener(listener: (View, Int) -> Unit) {
        postOptionsClickListener = listener
    }

    inner class FeedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.userName)
        val userDescription: TextView = itemView.findViewById(R.id.userDescription)
        val reviewText: TextView = itemView.findViewById(R.id.reviewText)
        val moreInfoButton: TextView = itemView.findViewById(R.id.moreInfoButton)
        val likeButton: ImageView = itemView.findViewById(R.id.likeButton)
        val postOptionsButton: ImageButton = itemView.findViewById(R.id.postOptionsButton)
        val commentsButton: ImageButton = itemView.findViewById(R.id.commentsButton)

        fun bind(feedItem: FeedItem) {
            userName.text = feedItem.userName
            userDescription.text = feedItem.userDescription
            reviewText.text = feedItem.reviewText

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