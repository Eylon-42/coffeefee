package com.eaor.coffeefee.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.utils.ImageLoader

class ImageAdapter(
    private val images: List<Uri>,
    private val onImageClick: (Int) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.postImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Use ImageLoader for consistent image loading
        ImageLoader.loadPostImage(
            holder.imageView,
            images[position].toString(),
            R.drawable.placeholder
        )
            
        holder.itemView.setOnClickListener { onImageClick(position) }
    }

    override fun getItemCount() = images.size
}