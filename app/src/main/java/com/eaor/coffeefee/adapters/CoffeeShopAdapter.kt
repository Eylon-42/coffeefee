package com.eaor.coffeefee.adapters

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

    private var filteredCoffeeShops = coffeeShops.toList()

    // Add click listener
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
        val coffeeShop = filteredCoffeeShops[position]
        holder.shopName.text = coffeeShop.name
        
        if (showCaptions) {
            holder.caption.visibility = View.VISIBLE
            holder.caption.text = coffeeShop.caption
        } else {
            holder.caption.visibility = View.GONE
        }

        // Set click listener
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(coffeeShop)
        }

        // Update rating hearts
        coffeeShop.rating?.let { rating ->
            val flooredRating = rating.toInt()
            val hasHalf = (rating - flooredRating) >= 0.5

            holder.ratingHearts.forEachIndexed { index, heart ->
                val resource = when {
                    index < flooredRating -> R.drawable.ic_heart_filled
                    index == flooredRating && hasHalf -> R.drawable.ic_heart_half
                    else -> R.drawable.ic_heart_outline
                }
                heart.setImageResource(resource)
                heart.setColorFilter(
                    ContextCompat.getColor(heart.context, R.color.coffee_primary)
                )
            }
        }
    }

    override fun getItemCount() = filteredCoffeeShops.size

    fun filter(query: String) {
        filteredCoffeeShops = if (query.isEmpty()) {
            coffeeShops
        } else {
            coffeeShops.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.caption.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }
}