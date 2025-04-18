package com.eaor.coffeefee

/**
 * GlobalState singleton to maintain application-wide state variables
 * that need to be accessed from multiple components.
 */
object GlobalState {
    /**
     * Flags to indicate which screens should be refreshed on next view
     * These are used when data is modified outside the respective fragments
     */
    var shouldRefreshFeed: Boolean = false
    var shouldRefreshProfile: Boolean = false
    var shouldRefreshCoffeeShops: Boolean = false
    
    /**
     * Flag to indicate if posts were actually added/updated/deleted
     * Used to distinguish between post content changes vs. just user data updates
     */
    var postsWereChanged: Boolean = false
    
    /**
     * Flag to indicate if user profile data was actually changed
     * Used to optimize refresh operations
     */
    var profileDataChanged: Boolean = false
    
    /**
     * Flag to indicate if a profile was just edited in the profile editor
     * Used to specifically track edits from the profile editor
     */
    var profileWasEdited: Boolean = false
    
    /**
     * Reset all refresh flags
     */
    fun resetAllRefreshFlags() {
        shouldRefreshFeed = false
        shouldRefreshProfile = false
        shouldRefreshCoffeeShops = false
        postsWereChanged = false
        profileDataChanged = false
        profileWasEdited = false
    }
    
    /**
     * Set all refresh flags when content is created or edited.
     * This ensures all views update with the latest data.
     */
    fun triggerRefreshAfterContentChange() {
        shouldRefreshFeed = true
        shouldRefreshProfile = true
        shouldRefreshCoffeeShops = true
        postsWereChanged = true  // Indicate actual post changes happened
    }
    
    /**
     * Set all flags related to user profile changes.
     * This ensures all views update with the latest user data.
     * @param dataChanged Whether profile data was actually modified
     */
    fun triggerRefreshAfterProfileChange(dataChanged: Boolean = false) {
        shouldRefreshFeed = dataChanged  // Only refresh feed if data actually changed
        shouldRefreshProfile = true
        profileDataChanged = dataChanged  // Track if actual changes were made
        profileWasEdited = true  // Indicate the profile was edited in the editor
    }
    
    /**
     * Set refresh flags when comments are added, updated, or deleted.
     * This ensures feed and profile views update with the latest comment counts.
     */
    fun triggerRefreshAfterCommentChange() {
        shouldRefreshFeed = true
        shouldRefreshProfile = true
        profileDataChanged = true  // Mark that profile data has changed via comments
        // Don't set postsWereChanged since only comment data changed, not post content
    }
} 