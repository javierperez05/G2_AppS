package com.example.cosmos.Model.Users

import com.example.cosmos.Model.Event.Event

data class Group(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val memberIds: List<String> = emptyList(),
    val eventIds: List<String> = emptyList()
)