package com.eaor.coffeefee.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.CoffeeShop

/**
 * Adapter for displaying coffee categories with their coffees
 */
class CoffeeCategoryAdapter(
    private val context: Context,
    private var categories: Map<String, List<CoffeeShop>> = emptyMap()
) : RecyclerView.Adapter<CoffeeCategoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryTitle: TextView = view.findViewById(R.id.categoryTitle)
        val coffeesRecyclerView: RecyclerView = view.findViewById(R.id.categoryItemsRecyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_coffee_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val categoryName = categories.keys.elementAt(position)
        val coffeeShops = categories[categoryName] ?: emptyList()
        
        holder.categoryTitle.text = categoryName
        
        // Setup nested RecyclerView for coffees in this category
        val adapter = SimpleCoffeeShopAdapter(context, coffeeShops)
        holder.coffeesRecyclerView.layoutManager = 
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        holder.coffeesRecyclerView.adapter = adapter
    }

    override fun getItemCount(): Int = categories.size

    fun updateCategories(newCategories: Map<String, List<CoffeeShop>>) {
        categories = newCategories
        notifyDataSetChanged()
    }
    
    /**
     * Simple adapter for coffee shop items within a category
     */
    inner class SimpleCoffeeShopAdapter(
        private val context: Context,
        private val coffeeShops: List<CoffeeShop>
    ) : RecyclerView.Adapter<SimpleCoffeeShopAdapter.CoffeeShopViewHolder>() {
        
        inner class CoffeeShopViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val coffeeName: TextView = view.findViewById(R.id.coffeeName)
            // Note: price is not used as it's not in the CoffeeShop model
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoffeeShopViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_simple_coffee, parent, false)
            return CoffeeShopViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: CoffeeShopViewHolder, position: Int) {
            val coffeeShop = coffeeShops[position]
            holder.coffeeName.text = coffeeShop.name
            // Price field is not set as it's not in the CoffeeShop model
        }
        
        override fun getItemCount(): Int = coffeeShops.size
    }
} 