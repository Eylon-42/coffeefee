package com.eaor.coffeefee.adapters // Use your actual package name

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R

class CoffeeShopAdapter(private var coffeeShops: List<String>) :
    RecyclerView.Adapter<CoffeeShopAdapter.ViewHolder>() {

    private var filteredCoffeeShops = coffeeShops.toMutableList()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_coffee_shop, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.title.text = filteredCoffeeShops[position]
    }

    override fun getItemCount(): Int = filteredCoffeeShops.size

    fun filter(query: String) {
        filteredCoffeeShops = if (query.isEmpty()) {
            coffeeShops.toMutableList()
        } else {
            coffeeShops.filter { it.contains(query, ignoreCase = true) }.toMutableList()
        }
        notifyDataSetChanged()
    }
}