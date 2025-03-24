package com.eaor.coffeefee.utils

import android.util.Log
import android.widget.ImageView
import com.eaor.coffeefee.R
import com.eaor.coffeefee.utils.CircleTransform
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import com.squareup.picasso.Transformation
import com.squareup.picasso.MemoryPolicy

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
     * @param forceRefresh Whether to force a network request
     */
    fun loadProfileImage(
        imageView: ImageView, 
        imageUrl: String?, 
        placeholder: Int = R.drawable.default_avatar,
        circular: Boolean = true,
        forceRefresh: Boolean = false
    ) {
        try {
            if (imageUrl.isNullOrEmpty()) {
                imageView.setImageResource(placeholder)
                return
            }
            
            val requestCreator = if (forceRefresh) {
                // Only force network request if explicitly asked
                Picasso.get()
                    .load(imageUrl)
                    .memoryPolicy(MemoryPolicy.NO_CACHE)
                    .networkPolicy(NetworkPolicy.NO_CACHE)
            } else {
                // Use cached version by default for profile images
                Picasso.get()
                    .load(imageUrl)
                    .networkPolicy(NetworkPolicy.OFFLINE)
            }
            
            // Apply common transformations and settings
            requestCreator
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
                    Log.d("ImageLoader", "Successfully loaded profile image: $imageUrl (cached)")
                }
                
                override fun onError(e: Exception?) {
                    // If offline loading failed, try from network as fallback
                    if (!forceRefresh) {
                        Log.d("ImageLoader", "Cache miss, loading from network: $imageUrl")
                        Picasso.get()
                            .load(imageUrl)
                            .placeholder(placeholder)
                            .error(placeholder)
                            .resize(MAX_PROFILE_WIDTH, MAX_PROFILE_HEIGHT)
                            .centerCrop()
                            .apply {
                                if (circular) transform(CircleTransform())
                            }
                            .into(imageView)
                    } else {
                        Log.e("ImageLoader", "Error loading profile image: $imageUrl - ${e?.message}")
                        imageView.setImageResource(placeholder)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("ImageLoader", "Error loading profile image: ${e.message}")
            imageView.setImageResource(placeholder)
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