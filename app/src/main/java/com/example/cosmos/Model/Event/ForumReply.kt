package com.example.cosmos.Model.Event

data class ForumReply(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Long = 0L
)
