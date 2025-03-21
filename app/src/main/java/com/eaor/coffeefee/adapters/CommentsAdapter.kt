package com.eaor.coffeefee.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
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
        
        // Log all data for debugging
        Log.d("CommentsAdapter", "Binding comment at position $position:")
        Log.d("CommentsAdapter", "  -> id: ${comment.id}")
        Log.d("CommentsAdapter", "  -> userId: ${comment.userId}")
        Log.d("CommentsAdapter", "  -> userName: '${comment.userName}'")
        Log.d("CommentsAdapter", "  -> photoUrl: ${comment.userPhotoUrl}")
        Log.d("CommentsAdapter", "  -> text: ${comment.text}")
        
        holder.commentText.text = comment.text
        
        // Set username with advanced fallback
        val displayName = if (comment.userName.isBlank()) "User" else comment.userName
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
                Log.d("CommentsAdapter", "Loading profile image: ${comment.userPhotoUrl}")
                
                // Apply Glide with proper circular transformation
                Glide.with(context)
                    .load(comment.userPhotoUrl)
                    .apply(RequestOptions.circleCropTransform()
                        .placeholder(R.drawable.default_avatar)
                        .error(R.drawable.default_avatar)
                        .override(120, 120)
                        .centerCrop())
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                    .into(holder.userImage)
            } else {
                Log.d("CommentsAdapter", "No profile image URL for user: ${comment.userId}")
                // Load default placeholder image
                Glide.with(context)
                    .load(R.drawable.default_avatar)
                    .apply(RequestOptions.circleCropTransform())
                    .into(holder.userImage)
            }
        } catch (e: Exception) {
            Log.e("CommentsAdapter", "Error loading profile image: ${e.message}")
            // Fallback in case of any error
            Glide.with(context)
                .load(R.drawable.default_avatar)
                .apply(RequestOptions.circleCropTransform())
                .into(holder.userImage)
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
        Log.d("CommentsAdapter", "Updating adapter with ${newComments.size} comments")
        this.comments = newComments
        notifyDataSetChanged()
    }
    
    /**
     * Updates a specific user's data in all their comments
     * This is called when user profile data changes
     */
    fun updateUserData(userId: String, userName: String, userPhotoUrl: String?) {
        Log.d("CommentsAdapter", "Updating user data for $userId: name=$userName, photo=$userPhotoUrl")
        var updatedCount = 0
        
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
        
        Log.d("CommentsAdapter", "Updated $updatedCount comments with new user data")
    }
    
    fun refreshUser(userId: String) {
        Log.d("CommentsAdapter", "Refreshing user data for: $userId")
        
        // Find all comments by this user and signal they may need refresh
        comments.forEachIndexed { index, comment ->
            if (comment.userId == userId) {
                Log.d("CommentsAdapter", "User $userId has comment at position $index, refreshing")
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