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
import com.eaor.coffeefee.models.CoffeeShop

class CoffeeShopAdapter(
    private var coffeeShops: List<CoffeeShop>,
    private val showCaptions: Boolean = false
) : RecyclerView.Adapter<CoffeeShopAdapter.ViewHolder>() {

    // Define listener interface
    interface ShopListener {
        fun onShopClicked(shop: CoffeeShop)
    }

    // Constructor with context and listener
    constructor(
        context: Context,
        coffeeShops: List<CoffeeShop>,
        listener: ShopListener
    ) : this(coffeeShops, true) {
        this.listener = listener
    }

    private var filteredCoffeeShops = coffeeShops.toList()
    private var listener: ShopListener? = null
    private var onItemClick: ((CoffeeShop) -> Unit)? = null

    fun setOnItemClickListener(listener: (CoffeeShop) -> Unit) {
        onItemClick = listener
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val shopName: TextView = view.findViewById(R.id.shopName)
        val caption: TextView = view.findViewById(R.id.caption)
        val ratingHearts: List<ImageView> = listOf(
            view.findViewById(R.id.heart1),
            view.findViewById(R.id.heart2),
            view.findViewById(R.id.heart3),
            view.findViewById(R.id.heart4),
            view.findViewById(R.id.heart5)
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_coffee_shop, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val shop = filteredCoffeeShops[position]
        
        holder.shopName.text = shop.name
        holder.caption.visibility = if (showCaptions) View.VISIBLE else View.GONE
        
        if (showCaptions) {
            holder.caption.text = shop.caption
        }
        
        // Set up rating hearts
        setupRatingHearts(holder, shop.rating)
        
        // Set click listener
        holder.itemView.setOnClickListener {
            listener?.onShopClicked(shop) ?: onItemClick?.invoke(shop)
        }
    }
    
    private fun setupRatingHearts(holder: ViewHolder, rating: Float?) {
        // Reset all hearts to empty
        for (heart in holder.ratingHearts) {
            heart.setImageResource(R.drawable.ic_heart_outline)
            heart.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.coffee_primary))
        }
        
        if (rating != null && rating > 0) {
            val fullHearts = rating.toInt()
            val hasHalfHeart = (rating - fullHearts) >= 0.5f
            
            // Fill the appropriate hearts
            for (i in holder.ratingHearts.indices) {
                when {
                    i < fullHearts -> {
                        holder.ratingHearts[i].setImageResource(R.drawable.ic_heart_filled)
                    }
                    i == fullHearts && hasHalfHeart -> {
                        holder.ratingHearts[i].setImageResource(R.drawable.ic_heart_half)
                    }
                    else -> {
                        // Leave as outline
                    }
                }
                holder.ratingHearts[i].setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.coffee_primary))
            }
        }
    }

    override fun getItemCount(): Int = filteredCoffeeShops.size
    
    fun updateData(newData: List<CoffeeShop>) {
        coffeeShops = newData
        filteredCoffeeShops = newData
        notifyDataSetChanged()
    }
    
    fun filter(query: String) {
        if (query.isEmpty()) {
            filteredCoffeeShops = coffeeShops
        } else {
            filteredCoffeeShops = coffeeShops.filter { 
                it.name.contains(query, ignoreCase = true) 
            }
        }
        notifyDataSetChanged()
    }
}