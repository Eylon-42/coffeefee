package com.eaor.coffeefee.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.Comment // Assuming you have a Comment data class

class CommentsAdapter(private val comments: List<Comment>) : RecyclerView.Adapter<CommentsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val commentText: TextView = view.findViewById(R.id.commentText) // Assuming you have a TextView for comment text
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false) // Assuming you have a layout for individual comments
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = comments[position]
        holder.commentText.text = comment.text // Assuming your Comment class has a 'text' property
    }

    override fun getItemCount(): Int {
        return comments.size
    }
}