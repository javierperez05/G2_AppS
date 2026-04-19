package com.example.cosmos.Model.Event

import com.example.cosmos.Model.Users.User

data class EventItem(
    val id: String? = null,
    val name: String? = null,
    val price: Double = 0.0,
    val count: Int = 0,
    val responsibleUserIds: List<String> = emptyList() // ¿Quién lleva las cervezas?
)