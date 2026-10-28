package com.example.customme

import com.google.firebase.firestore.DocumentId

data class Post(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val complexity: String = "",
    val imageUrls: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val coverUrl: String?
        get() = imageUrls.firstOrNull()
}
