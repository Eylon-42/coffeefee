package com.eaor.coffeefee.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.Comment // Assuming you have a Comment data class
import com.squareup.picasso.Picasso

class CommentsAdapter(
    private var comments: MutableList<Comment>,
    private val currentUserId: String,
    private val onCommentDelete: (Comment) -> Unit,
    private val onCommentEdit: (Comment) -> Unit
) : RecyclerView.Adapter<CommentsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userAvatar: ImageView = view.findViewById(R.id.userAvatar)
        val userName: TextView = view.findViewById(R.id.userName)
        val commentText: TextView = view.findViewById(R.id.commentText)
        val optionsButton: ImageButton = view.findViewById(R.id.commentOptionsButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false) // Assuming you have a layout for individual comments
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = comments[position]
        holder.userName.text = comment.userName
        holder.commentText.text = comment.text

        // Show options button only for user's own comments
        holder.optionsButton.visibility = if (comment.userId == currentUserId) View.VISIBLE else View.GONE
        
        holder.optionsButton.setOnClickListener { view ->
            showCommentOptions(view, comment)
        }

        // Load avatar
        if (!comment.userPhotoUrl.isNullOrEmpty()) {
            Picasso.get()
                .load(comment.userPhotoUrl)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(holder.userAvatar)
        } else {
            holder.userAvatar.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun showCommentOptions(view: View, comment: Comment) {
        PopupMenu(view.context, view).apply {
            menuInflater.inflate(R.menu.comment_options_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
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
            show()
        }
    }

    fun updateComments(newComments: List<Comment>) {
        comments.clear()
        comments.addAll(newComments)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return comments.size
    }
}