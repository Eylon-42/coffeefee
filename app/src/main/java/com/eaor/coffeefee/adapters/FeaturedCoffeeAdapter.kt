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
import com.eaor.coffeefee.models.Coffee
import com.squareup.picasso.Picasso

/**
 * Adapter for displaying featured coffee items
 */
class FeaturedCoffeeAdapter(
    private val context: Context,
    private var coffees: List<Coffee>,
    private val listener: CoffeeListener
) : RecyclerView.Adapter<FeaturedCoffeeAdapter.ViewHolder>() {

    interface CoffeeListener {
        fun onCoffeeClicked(coffee: Coffee)
        fun onAddToFavoritesClicked(coffee: Coffee)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val coffeeImage: ImageView = view.findViewById(R.id.coffeeImage)
        val coffeeName: TextView = view.findViewById(R.id.coffeeName)
        val coffeeDescription: TextView = view.findViewById(R.id.coffeeDescription)
        val coffeePrice: TextView = view.findViewById(R.id.coffeePrice)
        val favoriteButton: ImageButton = view.findViewById(R.id.favoriteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_featured_coffee, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val coffee = coffees[position]
        
        holder.coffeeName.text = coffee.name
        holder.coffeeDescription.text = coffee.description
        holder.coffeePrice.text = String.format("$%.2f", coffee.price)
        
        // Load coffee image with Picasso
        if (coffee.imageUrl.isNotEmpty()) {
            Picasso.get()
                .load(coffee.imageUrl)
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder)
                .into(holder.coffeeImage)
        } else {
            holder.coffeeImage.setImageResource(R.drawable.placeholder)
        }
        
        // Set click listeners
        holder.itemView.setOnClickListener {
            listener.onCoffeeClicked(coffee)
        }
        
        holder.favoriteButton.setOnClickListener {
            listener.onAddToFavoritesClicked(coffee)
        }
    }

    override fun getItemCount(): Int = coffees.size

    fun updateCoffees(newCoffees: List<Coffee>) {
        coffees = newCoffees
        notifyDataSetChanged()
    }
} 