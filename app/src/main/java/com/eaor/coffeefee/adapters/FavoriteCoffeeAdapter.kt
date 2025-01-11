package com.eaor.coffeefee.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.CoffeeShop

class FavoriteCoffeeAdapter(
    private val coffeeShops: List<CoffeeShop>,
    private val onItemClick: (CoffeeShop) -> Unit
) : RecyclerView.Adapter<FavoriteCoffeeAdapter.ViewHolder>() {

    private val likedStates = mutableMapOf<Int, Boolean>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val coffeeName: TextView = view.findViewById(R.id.coffeeName)
        val favoriteButton: ImageButton = view.findViewById(R.id.favoriteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_coffee, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val coffeeShop = coffeeShops[position]
        
        // Set coffee shop name
        holder.coffeeName.text = coffeeShop.name
        
        // Set initial like state
        val isLiked = likedStates[position] ?: true  // Default to true for favorites
        updateLikeButton(holder.favoriteButton, isLiked)
        
        // Set favorite button click listener
        holder.favoriteButton.setOnClickListener {
            val newLikedState = !(likedStates[position] ?: true)
            likedStates[position] = newLikedState
            updateLikeButton(holder.favoriteButton, newLikedState)
        }
        
        // Set click listener for the entire item
        holder.itemView.setOnClickListener {
            onItemClick(coffeeShop)
        }
    }

    private fun updateLikeButton(button: ImageButton, isLiked: Boolean) {
        button.setImageResource(
            if (isLiked) R.drawable.ic_heart_filled
            else R.drawable.ic_heart_outline
        )
        button.setColorFilter(
            ContextCompat.getColor(button.context, R.color.coffee_primary)
        )
    }

    override fun getItemCount() = coffeeShops.size
} 