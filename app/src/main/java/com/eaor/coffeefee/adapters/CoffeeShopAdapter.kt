package com.eaor.coffeefee.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.eaor.coffeefee.R
import com.eaor.coffeefee.models.CoffeeShop
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class CoffeeShopAdapter(
    private var coffeeShops: List<CoffeeShop>,
    private val showCaptions: Boolean = false,
    private val matchingTagsMap: Map<String?, List<String>> = emptyMap()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var filteredCoffeeShops = coffeeShops.toList()
    
    companion object {
        private const val VIEW_TYPE_COFFEE_SHOP = 0
        private const val VIEW_TYPE_SUGGESTION = 1
    }

    // Add click listener
    private var onItemClick: ((CoffeeShop) -> Unit)? = null
    private var onFavoriteClick: ((CoffeeShop, Boolean) -> Unit)? = null

    fun setOnItemClickListener(listener: (CoffeeShop) -> Unit) {
        onItemClick = listener
    }
    
    fun setOnFavoriteClickListener(listener: (CoffeeShop, Boolean) -> Unit) {
        onFavoriteClick = listener
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
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val matchingTagsChipGroup: ChipGroup = view.findViewById(R.id.matchingTagsChipGroup)
        val matchingTagsLabel: TextView = view.findViewById(R.id.matchingTagsLabel)
        val matchingReasonsLayout: ViewGroup = view.findViewById(R.id.matchingReasonsLayout)
        val matchingReasonsTextView: TextView = view.findViewById(R.id.matchingReasonsTextView)
    }
    
    class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val coffeeName: TextView = view.findViewById(R.id.coffeeName)
        val coffeeImage: ImageView = view.findViewById(R.id.coffeeImage)
        val favoriteButton: ImageButton = view.findViewById(R.id.favoriteButton)
        val suggestionReason: TextView = view.findViewById(R.id.suggestionReason)
    }

    override fun getItemViewType(position: Int): Int {
        val coffeeShop = filteredCoffeeShops[position]
        return if (matchingTagsMap.containsKey(coffeeShop.placeId)) {
            VIEW_TYPE_SUGGESTION
        } else {
            VIEW_TYPE_COFFEE_SHOP
        }
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
        val coffeeShop = filteredCoffeeShops[position]
        
        when (holder) {
            is CoffeeShopViewHolder -> bindCoffeeShopViewHolder(holder, coffeeShop)
            is SuggestionViewHolder -> bindSuggestionViewHolder(holder, coffeeShop)
        }
    }
    
    private fun bindCoffeeShopViewHolder(holder: CoffeeShopViewHolder, coffeeShop: CoffeeShop) {
        holder.shopName.text = coffeeShop.name
        
        if (showCaptions) {
            holder.caption.visibility = View.VISIBLE
            holder.caption.text = coffeeShop.caption
        } else {
            holder.caption.visibility = View.GONE
        }

        // Load image if available
        if (!coffeeShop.photoUrl.isNullOrEmpty()) {
            Glide.with(holder.imageView.context)
                .load(coffeeShop.photoUrl)
                .placeholder(R.drawable.placeholder as Int)
                .into(holder.imageView)
        } else {
            holder.imageView.setImageResource(R.drawable.placeholder)
        }

        // Set matching tags if available
        val matchingTags = matchingTagsMap[coffeeShop.placeId]
        if (matchingTags != null && matchingTags.isNotEmpty()) {
            holder.matchingTagsChipGroup.visibility = View.VISIBLE
            holder.matchingTagsLabel.visibility = View.VISIBLE
            holder.matchingTagsChipGroup.removeAllViews()
            
            // Check if the matchingTags list contains string reasons rather than just tags
            val isReasons = matchingTags.any { it.length > 15 || it.contains(" ") }
            
            if (isReasons) {
                // These are AI reasons, not tags, so display them in the reasons layout
                holder.matchingTagsChipGroup.visibility = View.GONE
                holder.matchingTagsLabel.visibility = View.GONE
                
                holder.matchingReasonsLayout.visibility = View.VISIBLE
                val reasons = matchingTags.joinToString("\n• ", "• ")
                holder.matchingReasonsTextView.text = reasons
            } else {
                // These are regular tags, display in chip group
                holder.matchingReasonsLayout.visibility = View.GONE
                
                for (tag in matchingTags) {
                    val chip = LayoutInflater.from(holder.matchingTagsChipGroup.context)
                        .inflate(R.layout.item_tag_chip, holder.matchingTagsChipGroup, false) as Chip
                    chip.text = tag
                    holder.matchingTagsChipGroup.addView(chip)
                }
            }
        } else {
            holder.matchingTagsChipGroup.visibility = View.GONE
            holder.matchingTagsLabel.visibility = View.GONE
            holder.matchingReasonsLayout.visibility = View.GONE
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
    
    private fun bindSuggestionViewHolder(holder: SuggestionViewHolder, coffeeShop: CoffeeShop) {
        holder.coffeeName.text = coffeeShop.name
        
        // Set suggestion reason
        val matchingTags = matchingTagsMap[coffeeShop.placeId] ?: emptyList()
        if (matchingTags.isNotEmpty()) {
            holder.suggestionReason.visibility = View.VISIBLE
            holder.suggestionReason.text = "Suggested because: ${matchingTags.joinToString(", ")}"
        } else {
            holder.suggestionReason.visibility = View.GONE
        }
        
        // Set image if available
        coffeeShop.photoUrl?.let { url ->
            // Load image with your preferred library (Glide, Picasso, etc.)
            // For example: Glide.with(holder.itemView).load(url).into(holder.coffeeImage)
        }
        
        // Set favorite button click listener
        holder.favoriteButton.setOnClickListener {
            val isFavorite = holder.favoriteButton.tag as? Boolean ?: false
            val newState = !isFavorite
            holder.favoriteButton.tag = newState
            
            // Update button appearance
            holder.favoriteButton.setImageResource(
                if (newState) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            
            // Trigger callback
            onFavoriteClick?.invoke(coffeeShop, newState)
        }
        
        // Set item click listener
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(coffeeShop)
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

    fun updateData(newData: List<CoffeeShop>) {
        coffeeShops = newData
        filteredCoffeeShops = newData
        notifyDataSetChanged()
    }
}