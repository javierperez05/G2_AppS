package com.example.cosmos.Model.Event

data class ForumThread(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
    val replies: List<ForumReply> = emptyList()
)
