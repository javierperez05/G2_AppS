package com.example.cosmos.Model.Users

data class Group(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val memberIds: List<String> = emptyList(),
    val adminIds: List<String> = emptyList(),
    val eventIds: List<String> = emptyList(),
    val invitedIds: List<String> = emptyList()
)