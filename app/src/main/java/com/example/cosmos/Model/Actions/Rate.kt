package com.example.cosmos.Model.Actions

import com.example.cosmos.Model.Users.Group
import com.example.cosmos.Model.Users.User

data class Rate(
    val id: String? = null,
    val userId: String? = null,
    val eventId: String? = null,
    val rating: Float = 0f,
    val comment: String? = null,
    val images: List<String> = emptyList()
)