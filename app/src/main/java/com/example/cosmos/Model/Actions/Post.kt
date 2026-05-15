package com.example.cosmos.Model.Actions

data class Post(
    val id: String? = null,
    val userId: String? = null,
    val username: String? = null,
    val eventId: String? = null,
    val eventTitle: String? = null,
    val eventDescription: String? = null,
    val eventLocation: String? = null,
    val rating: Float = 0f,
    val comment: String? = null,
    val imageUrls: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val itemSummary: String? = null
)
