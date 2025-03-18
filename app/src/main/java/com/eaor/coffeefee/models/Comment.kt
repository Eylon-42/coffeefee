package com.eaor.coffeefee.models

data class Comment(
    val id: String = "", // Firestore document ID
    val postId: String = "", // ID of the post the comment belongs to
    val userId: String = "", // ID of the user who made the comment
    val text: String = "", // The content of the comment
    val timestamp: Long = System.currentTimeMillis(), // Timestamp of when the comment was made
    var userName: String = "", // Transient field, not stored in Firestore
    var userPhotoUrl: String? = null // Transient field, not stored in Firestore
) {
    // Convert to HashMap for Firestore - only store essential fields
    fun toMap(): Map<String, Any> {
        return hashMapOf(
            "id" to id,
            "postId" to postId,
            "userId" to userId,
            "text" to text,
            "timestamp" to timestamp
        )
    }
}