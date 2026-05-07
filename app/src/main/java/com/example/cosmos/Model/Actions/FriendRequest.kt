package com.example.cosmos.Model.Actions

data class FriendRequest(
    val id: String? = null,
    val fromId: String = "",
    val toId: String = "",
    val fromUsername: String = "",
    val status: String = "pending" // pending, accepted, rejected
)
