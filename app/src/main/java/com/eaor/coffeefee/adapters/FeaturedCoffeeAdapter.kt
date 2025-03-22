package com.eaor.coffeefee.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.CoffeeShop
import com.squareup.picasso.Picasso

/**
 * Adapter for displaying featured coffee items
 */
class FeaturedCoffeeAdapter(
    private val context: Context,
    private var coffeeShops: List<CoffeeShop>,
    private val listener: CoffeeShopListener
) : RecyclerView.Adapter<FeaturedCoffeeAdapter.ViewHolder>() {

    interface CoffeeShopListener {
        fun onCoffeeShopClicked(coffeeShop: CoffeeShop)
        fun onAddToFavoritesClicked(coffeeShop: CoffeeShop)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val coffeeImage: ImageView = view.findViewById(R.id.coffeeImage)
        val coffeeName: TextView = view.findViewById(R.id.coffeeName)
        val coffeeDescription: TextView = view.findViewById(R.id.coffeeDescription)
        val favoriteButton: ImageButton = view.findViewById(R.id.favoriteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_featured_coffee, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val coffeeShop = coffeeShops[position]
        
        holder.coffeeName.text = coffeeShop.name
        holder.coffeeDescription.text = coffeeShop.description
        
        // Load coffee image with Picasso
        if (coffeeShop.photoUrl != null) {
            Picasso.get()
                .load(coffeeShop.photoUrl)
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder)
                .into(holder.coffeeImage)
        } else {
            holder.coffeeImage.setImageResource(R.drawable.placeholder)
        }
        
        // Set click listeners
        holder.itemView.setOnClickListener {
            listener.onCoffeeShopClicked(coffeeShop)
        }
        
        holder.favoriteButton.setOnClickListener {
            listener.onAddToFavoritesClicked(coffeeShop)
        }
    }

    override fun getItemCount(): Int = coffeeShops.size

    fun updateCoffeeShops(newCoffeeShops: List<CoffeeShop>) {
        coffeeShops = newCoffeeShops
        notifyDataSetChanged()
    }
} 