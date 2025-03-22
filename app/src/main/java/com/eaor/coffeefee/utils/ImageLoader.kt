package com.eaor.coffeefee.utils

import android.util.Log
import android.widget.ImageView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.utils.CircleTransform
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import com.squareup.picasso.Transformation

/**
 * Utility class for loading images across the app with consistent caching behavior
 */
object ImageLoader {
    // Maximum dimensions for images to prevent OOM errors
    private const val MAX_WIDTH = 1200
    private const val MAX_HEIGHT = 1200
    
    // Maximum dimensions for profile images (usually smaller)
    private const val MAX_PROFILE_WIDTH = 300
    private const val MAX_PROFILE_HEIGHT = 300
    
    /**
     * Load a user profile image with proper caching
     * Uses the imageView tag to avoid unnecessary reloads
     * 
     * @param imageView The ImageView to load into
     * @param imageUrl The URL of the image to load
     * @param placeholder Resource ID for the placeholder
     * @param circular Whether to make the image circular
     */
    fun loadProfileImage(
        imageView: ImageView, 
        imageUrl: String?, 
        placeholder: Int = R.drawable.default_avatar,
        circular: Boolean = true
    ) {
        try {
            if (!imageUrl.isNullOrEmpty()) {
                // Check if we're already loading/showing this URL to avoid flickering
                val currentTag = imageView.tag?.toString()
                if (currentTag != imageUrl) {
                    Log.d("ImageLoader", "Loading profile image: $imageUrl (previous: $currentTag)")
                    
                    // Tag the imageView with the URL being loaded
                    imageView.tag = imageUrl
                    
                    // Create a request that uses both memory and disk cache 
                    // without forcing offline-only
                    val requestCreator = Picasso.get()
                        .load(imageUrl)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .resize(MAX_PROFILE_WIDTH, MAX_PROFILE_HEIGHT)
                        .centerCrop()
                    
                    // Apply circle transform if needed
                    val finalRequest = if (circular) {
                        requestCreator.transform(CircleTransform())
                    } else {
                        requestCreator
                    }
                    
                    // Load with normal caching behavior
                    finalRequest.into(imageView, object : com.squareup.picasso.Callback {
                        override fun onSuccess() {
                            Log.d("ImageLoader", "Successfully loaded profile image: $imageUrl")
                        }
                        
                        override fun onError(e: Exception?) {
                            Log.e("ImageLoader", "Error loading profile image: $imageUrl - ${e?.message}")
                            imageView.setImageResource(placeholder)
                        }
                    })
                } else {
                    Log.d("ImageLoader", "Skipping reload, image already loaded: $imageUrl")
                }
            } else {
                // No URL, use placeholder and clear tag
                Log.d("ImageLoader", "No image URL provided, using placeholder")
                imageView.setImageResource(placeholder)
                imageView.tag = null
            }
        } catch (e: Exception) {
            Log.e("ImageLoader", "Error loading profile image: ${e.message}")
            imageView.setImageResource(placeholder)
            imageView.tag = null
        }
    }
    
    /**
     * Load a post image with proper caching
     * 
     * @param imageView The ImageView to load into
     * @param imageUrl The URL of the image to load
     * @param placeholder Resource ID for the placeholder
     */
    fun loadPostImage(
        imageView: ImageView, 
        imageUrl: String?, 
        placeholder: Int = R.drawable.placeholder
    ) {
        try {
            if (!imageUrl.isNullOrEmpty()) {
                // For post images, we don't use tag since they are unique per post
                Picasso.get()
                    .load(imageUrl)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .resize(MAX_WIDTH, MAX_HEIGHT)
                    .centerInside()
                    .onlyScaleDown() // Don't upscale small images
                    .into(imageView)
            } else {
                imageView.setImageResource(placeholder)
            }
        } catch (e: Exception) {
            Log.e("ImageLoader", "Error loading post image: ${e.message}")
            imageView.setImageResource(placeholder)
        }
    }
    
    /**
     * Invalidate a URL in Picasso's cache
     * 
     * @param url The URL to invalidate
     */
    fun invalidateCache(url: String?) {
        if (!url.isNullOrEmpty()) {
            try {
                Picasso.get().invalidate(url)
                Log.d("ImageLoader", "Invalidated cache for URL: $url")
            } catch (e: Exception) {
                Log.e("ImageLoader", "Error invalidating cache: ${e.message}")
            }
        }
    }
} 