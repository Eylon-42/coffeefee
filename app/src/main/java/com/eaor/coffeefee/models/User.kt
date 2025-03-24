package com.eaor.coffeefee.models

/**
 * Complete User model that matches the Firestore schema
 * This represents a user as stored in the "Users" collection in Firestore
 */
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profilePhotoUrl: String = "",
    val preferences: UserPreferences = UserPreferences(),
    val tags: List<String> = listOf()
)

/**
 * Nested preferences object for user preferences
 */
data class UserPreferences(
    val dietaryNeeds: String = "",
    val favoriteCoffeeDrink: String = "",
    val locationPreference: String = "",
    val preferredAtmosphere: String = ""
)

/**
 * Extension functions to convert between Firestore data and User model
 */
fun User.toMap(): Map<String, Any> {
    return mapOf(
        "name" to name,
        "email" to email,
        "profilePhotoUrl" to profilePhotoUrl,
        "preferences" to mapOf(
            "dietaryNeeds" to preferences.dietaryNeeds,
            "favoriteCoffeeDrink" to preferences.favoriteCoffeeDrink,
            "locationPreference" to preferences.locationPreference,
            "preferredAtmosphere" to preferences.preferredAtmosphere
        ),
        "tags" to tags
    )
}

fun Map<String, Any>.toUser(uid: String): User {
    val preferencesMap = this["preferences"] as? Map<String, Any> ?: mapOf()
    
    return User(
        uid = uid,
        name = this["name"] as? String ?: "",
        email = this["email"] as? String ?: "",
        profilePhotoUrl = this["profilePhotoUrl"] as? String ?: "",
        preferences = UserPreferences(
            dietaryNeeds = preferencesMap["dietaryNeeds"] as? String ?: "",
            favoriteCoffeeDrink = preferencesMap["favoriteCoffeeDrink"] as? String ?: "",
            locationPreference = preferencesMap["locationPreference"] as? String ?: "",
            preferredAtmosphere = preferencesMap["preferredAtmosphere"] as? String ?: ""
        ),
        tags = (this["tags"] as? List<*>)?.filterIsInstance<String>() ?: listOf()
    )
} 