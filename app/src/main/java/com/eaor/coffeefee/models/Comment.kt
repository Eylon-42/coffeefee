package com.eaor.coffeefee.models

data class Comment(
    val id: String, // Unique identifier for the comment
    val postId: String, // ID of the post the comment belongs to
    val userName: String, // Name of the user who made the comment
    val text: String, // The content of the comment
    val timestamp: Long // Timestamp of when the comment was made
)