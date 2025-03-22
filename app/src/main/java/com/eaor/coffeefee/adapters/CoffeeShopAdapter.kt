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
    private val showCaptions: Boolean = false,
    private val viewType: Int = VIEW_TYPE_COFFEE_SHOP,
    private val matchingReasonsMap: Map<String?, List<String>>? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_COFFEE_SHOP = 0
        const val VIEW_TYPE_SUGGESTION = 1
    }

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

    class CoffeeShopViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val shopName: TextView = view.findViewById(R.id.nameTextView)
        val caption: TextView = view.findViewById(R.id.captionTextView)
        val ratingHearts: List<ImageView> = listOf(
            view.findViewById(R.id.heart1),
            view.findViewById(R.id.heart2),
            view.findViewById(R.id.heart3),
            view.findViewById(R.id.heart4),
            view.findViewById(R.id.heart5)
        )
    }

    class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val coffeeName: TextView = view.findViewById(R.id.coffeeName)
        val tagsChipGroup: com.google.android.material.chip.ChipGroup = view.findViewById(R.id.tagsChipGroup)
        val suggestionReason: TextView = view.findViewById(R.id.suggestionReason)
    }

    override fun getItemViewType(position: Int): Int {
        return viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SUGGESTION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_favorite_coffee, parent, false)
                SuggestionViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_coffee_shop, parent, false)
                CoffeeShopViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val shop = filteredCoffeeShops[position]
        
        when (holder) {
            is CoffeeShopViewHolder -> bindCoffeeShopViewHolder(holder, shop)
            is SuggestionViewHolder -> bindSuggestionViewHolder(holder, shop)
        }
        
        // Set click listener
        holder.itemView.setOnClickListener {
            listener?.onShopClicked(shop) ?: onItemClick?.invoke(shop)
        }
    }
    
    private fun bindCoffeeShopViewHolder(holder: CoffeeShopViewHolder, coffeeShop: CoffeeShop) {
        holder.shopName.text = coffeeShop.name
        holder.caption.visibility = if (showCaptions) View.VISIBLE else View.GONE
        
        if (showCaptions) {
            holder.caption.text = coffeeShop.address
        }
        
        // Set up rating hearts
        setupRatingHearts(holder, coffeeShop.rating)
    }
    
    private fun bindSuggestionViewHolder(holder: SuggestionViewHolder, coffeeShop: CoffeeShop) {
        holder.coffeeName.text = coffeeShop.name
        
        // Show address
        holder.suggestionReason.text = coffeeShop.address
        holder.suggestionReason.visibility = View.VISIBLE
        
        holder.tagsChipGroup.removeAllViews() // Clear existing chips
        
        if (coffeeShop.tags.isNotEmpty()) {
            // Show up to 3 tags from the coffee shop object
            val tagsToShow = coffeeShop.tags.take(3)
            addTagsAsChips(holder.tagsChipGroup, tagsToShow, holder.itemView.context)
            holder.tagsChipGroup.visibility = View.VISIBLE
        } else {
            // No tags to display
            holder.tagsChipGroup.visibility = View.GONE
        }
    }
    
    private fun addTagsAsChips(
        chipGroup: com.google.android.material.chip.ChipGroup,
        tags: List<String>,
        context: Context
    ) {
        chipGroup.visibility = View.VISIBLE
        
        for (tag in tags) {
            val chip = com.google.android.material.chip.Chip(context).apply {
                text = tag
                isClickable = false
                isCheckable = false
                
                // Apply custom styling
                chipBackgroundColor = ContextCompat.getColorStateList(
                    context,
                    R.color.coffee_light
                )
                setTextColor(ContextCompat.getColor(context, R.color.coffee_primary_dark))
                chipStrokeWidth = 0f
                textSize = context.resources.getDimension(R.dimen.chip_text_size) / 
                           context.resources.displayMetrics.density
                
                // Set more compact size
                chipMinHeight = context.resources.getDimension(R.dimen.chip_min_height)
                chipStartPadding = context.resources.getDimension(R.dimen.chip_padding) / 2
                chipEndPadding = context.resources.getDimension(R.dimen.chip_padding) / 2
                
                // Make it more oval/rounded
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(chipMinHeight / 2)
                    .build()
            }
            chipGroup.addView(chip)
        }
    }
    
    private fun setupRatingHearts(holder: CoffeeShopViewHolder, rating: Float?) {
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