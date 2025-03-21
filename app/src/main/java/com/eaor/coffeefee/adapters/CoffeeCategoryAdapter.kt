package com.eaor.coffeefee.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.Coffee

/**
 * Adapter for displaying coffee categories with their coffees
 */
class CoffeeCategoryAdapter(
    private val context: Context,
    private var categories: Map<String, List<Coffee>> = emptyMap()
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
        val coffees = categories[categoryName] ?: emptyList()
        
        holder.categoryTitle.text = categoryName
        
        // Setup nested RecyclerView for coffees in this category
        val adapter = SimpleCoffeeAdapter(context, coffees)
        holder.coffeesRecyclerView.layoutManager = 
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        holder.coffeesRecyclerView.adapter = adapter
    }

    override fun getItemCount(): Int = categories.size

    fun updateCategories(newCategories: Map<String, List<Coffee>>) {
        categories = newCategories
        notifyDataSetChanged()
    }
    
    /**
     * Simple adapter for coffee items within a category
     */
    inner class SimpleCoffeeAdapter(
        private val context: Context,
        private val coffees: List<Coffee>
    ) : RecyclerView.Adapter<SimpleCoffeeAdapter.CoffeeViewHolder>() {
        
        inner class CoffeeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val coffeeName: TextView = view.findViewById(R.id.coffeeName)
            val coffeePrice: TextView = view.findViewById(R.id.coffeePrice)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoffeeViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_simple_coffee, parent, false)
            return CoffeeViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: CoffeeViewHolder, position: Int) {
            val coffee = coffees[position]
            holder.coffeeName.text = coffee.name
            holder.coffeePrice.text = String.format("$%.2f", coffee.price)
        }
        
        override fun getItemCount(): Int = coffees.size
    }
} 