package com.eaor.coffeefee.models

data class Comment(
    val id: String,
    val postId: String,
    val userId: String,
    val text: String,
    val timestamp: Long,
    var userName: String = "", // Temporary field used only for display
    var userPhotoUrl: String? = null // Temporary field used only for display
) {
    fun toMap(): Map<String, Any> {
        val map = HashMap<String, Any>()
        map["id"] = id
        map["postId"] = postId
        map["userId"] = userId
        map["text"] = text
        map["timestamp"] = timestamp
        
        // Don't store userName and userPhotoUrl in Firestore anymore
        // since they will be loaded from Users collection
        
        return map
    }
}