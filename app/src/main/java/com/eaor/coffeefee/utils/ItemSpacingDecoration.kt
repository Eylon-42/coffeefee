package com.eaor.coffeefee.utils

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * A RecyclerView decoration that adds consistent spacing between items
 * with special handling for the first and last items.
 */
class ItemSpacingDecoration(
    private val spacing: Int,
    private val includeEdge: Boolean = false
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val itemCount = state.itemCount
        
        if (includeEdge) {
            // If include edge spacing is true, add spacing to all items including first and last
            when (position) {
                0 -> {
                    // First item gets top spacing but not bottom
                    outRect.top = spacing
                    outRect.bottom = spacing / 2
                }
                itemCount - 1 -> {
                    // Last item gets bottom spacing but reduced top spacing
                    outRect.top = spacing / 2
                    outRect.bottom = spacing
                }
                else -> {
                    // Middle items get half spacing on top and bottom
                    outRect.top = spacing / 2
                    outRect.bottom = spacing / 2
                }
            }
        } else {
            // Without edge spacing, only add spacing between items
            if (position > 0) {
                outRect.top = spacing
            }
        }
    }
} 