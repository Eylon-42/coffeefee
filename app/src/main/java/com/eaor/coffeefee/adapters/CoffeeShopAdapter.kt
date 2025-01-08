package com.eaor.coffeefee.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.CoffeeShop

class CoffeeShopAdapter(private var coffeeShops: List<CoffeeShop>) :
    RecyclerView.Adapter<CoffeeShopAdapter.ViewHolder>() {

    private var filteredCoffeeShops = coffeeShops.toList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val shopName: TextView = view.findViewById(R.id.shopName)
        val caption: TextView = view.findViewById(R.id.caption)
        val ratingStars: List<ImageView> = listOf(
            view.findViewById(R.id.star1),
            view.findViewById(R.id.star2),
            view.findViewById(R.id.star3),
            view.findViewById(R.id.star4),
            view.findViewById(R.id.star5)
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
        holder.caption.text = coffeeShop.caption

        // Update rating stars
        val rating = coffeeShop.rating.toInt()
        holder.ratingStars.forEachIndexed { index, star ->
            star.setImageResource(
                if (index < rating) R.drawable.ic_heart_filled
                else R.drawable.ic_heart_outline
            )
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