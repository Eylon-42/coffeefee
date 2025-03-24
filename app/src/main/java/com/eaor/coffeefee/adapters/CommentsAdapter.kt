package com.eaor.coffeefee.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.Comment

class CommentsAdapter(
    private var comments: MutableList<Comment>,
    private val currentUserId: String = "",
    private val onCommentDelete: (Comment) -> Unit,
    private val onCommentEdit: (Comment) -> Unit
) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

    class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userName: TextView = view.findViewById(R.id.comment_user_name)
        val commentText: TextView = view.findViewById(R.id.comment_text)
        val timestamp: TextView = view.findViewById(R.id.comment_timestamp)
        val userImage: ImageView = view.findViewById(R.id.comment_user_image)
        val moreOptions: ImageView = view.findViewById(R.id.comment_more_options)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        
        // Set comment text - ALWAYS set this even if empty
        holder.commentText.text = comment.text.ifEmpty { "[Empty comment]" }
        
        // Set username with more aggressive fallback
        val displayName = when {
            comment.userName.isNotBlank() -> comment.userName
            else -> "User ${comment.userId.take(4)}" // Use part of the ID as identifier if no name
        }
        holder.userName.text = displayName
        
        // Set timestamp
        holder.timestamp.text = getRelativeTimeSpan(comment.timestamp)
        
        // Load user profile image
        loadProfileImage(holder, comment)
        
        // Show more options only for comments made by the current user
        if (comment.userId == currentUserId) {
            holder.moreOptions.visibility = View.VISIBLE
            setupMoreOptionsMenu(holder, comment)
        } else {
            holder.moreOptions.visibility = View.GONE
        }
    }
    
    private fun loadProfileImage(holder: CommentViewHolder, comment: Comment) {
        val context = holder.userImage.context
        
        try {
            if (comment.userPhotoUrl != null && comment.userPhotoUrl!!.isNotEmpty()) {
                // Try loading from cache first
                com.squareup.picasso.Picasso.get()
                    .load(comment.userPhotoUrl)
                    .networkPolicy(com.squareup.picasso.NetworkPolicy.OFFLINE)
                    .transform(com.eaor.coffeefee.utils.CircleTransform())
                    .placeholder(R.drawable.default_avatar)
                    .error(R.drawable.default_avatar)
                    .into(holder.userImage, object : com.squareup.picasso.Callback {
                        override fun onSuccess() {
                            // Image loaded successfully from cache
                        }
                        
                        override fun onError(e: Exception?) {
                            Log.e("CommentsAdapter", "Error loading profile image from cache: ${e?.message}")
                            // Try loading with network as fallback
                            com.squareup.picasso.Picasso.get()
                                .load(comment.userPhotoUrl)
                                .transform(com.eaor.coffeefee.utils.CircleTransform())
                                .placeholder(R.drawable.default_avatar)
                                .error(R.drawable.default_avatar)
                                .into(holder.userImage)
                        }
                    })
            } else {
                // Load default placeholder image
                holder.userImage.setImageResource(R.drawable.default_avatar)
            }
        } catch (e: Exception) {
            Log.e("CommentsAdapter", "Error loading profile image: ${e.message}")
            // Fallback in case of any error
            holder.userImage.setImageResource(R.drawable.default_avatar)
        }
    }

    private fun setupMoreOptionsMenu(holder: CommentViewHolder, comment: Comment) {
        holder.moreOptions.setOnClickListener { view ->
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.inflate(R.menu.comment_options_menu)
            
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit_comment -> {
                        onCommentEdit(comment)
                        true
                    }
                    R.id.action_delete_comment -> {
                        onCommentDelete(comment)
                        true
                    }
                    else -> false
                }
            }
            
            popupMenu.show()
        }
    }

    override fun getItemCount() = comments.size
    
    fun updateComments(newComments: MutableList<Comment>) {
        // Use DiffUtil to calculate changes instead of full notifyDataSetChanged
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = comments.size
            override fun getNewListSize(): Int = newComments.size
            
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return comments[oldItemPosition].id == newComments[newItemPosition].id
            }
            
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldComment = comments[oldItemPosition]
                val newComment = newComments[newItemPosition]
                return oldComment.text == newComment.text && 
                       oldComment.userName == newComment.userName &&
                       oldComment.userPhotoUrl == newComment.userPhotoUrl
            }
        })
        
        this.comments = newComments
        diffResult.dispatchUpdatesTo(this)
        
        // Force refresh the entire adapter if there are issues with DiffUtil
        if (itemCount > 0 && newComments.size > 0 && itemCount != newComments.size) {
            notifyDataSetChanged()
        }
    }
    
    /**
     * Updates a specific user's data in all their comments
     * This is called when user profile data changes
     */
    fun updateUserData(userId: String, userName: String, userPhotoUrl: String?) {
        var updatedCount = 0
        
        // Force Picasso to invalidate the cache for this URL to ensure fresh images
        if (userPhotoUrl != null && userPhotoUrl.isNotEmpty()) {
            try {
                com.squareup.picasso.Picasso.get().invalidate(userPhotoUrl)
            } catch (e: Exception) {
                Log.e("CommentsAdapter", "Failed to invalidate Picasso cache: ${e.message}")
            }
        }
        
        // Find all comments by this user and update them
        for (i in comments.indices) {
            if (comments[i].userId == userId) {
                comments[i] = comments[i].copy(
                    userName = userName,
                    userPhotoUrl = userPhotoUrl
                )
                notifyItemChanged(i)
                updatedCount++
            }
        }
    }
    
    fun refreshUser(userId: String) {
        // Find all comments by this user and signal they may need refresh
        comments.forEachIndexed { index, comment ->
            if (comment.userId == userId) {
                notifyItemChanged(index)
            }
        }
    }

    /**
     * Formats a timestamp into a relative time string like "1m ago", "1h ago", "1d ago"
     * Uses more precise calculations for better user experience.
     */
    private fun getRelativeTimeSpan(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> {
                val minutes = diff / 60_000
                "${minutes}m ago"
            }
            diff < 86_400_000 -> {
                val hours = diff / 3_600_000
                "${hours}h ago"
            }
            diff < 604_800_000 -> {
                val days = diff / 86_400_000
                "${days}d ago"
            }
            diff < 2_592_000_000 -> {
                val weeks = diff / 604_800_000
                "${weeks}w ago"
            }
            diff < 31_536_000_000 -> {
                val months = diff / 2_592_000_000
                "${months}mo ago"
            }
            else -> {
                val years = diff / 31_536_000_000
                "${years}y ago"
            }
        }
    }
}