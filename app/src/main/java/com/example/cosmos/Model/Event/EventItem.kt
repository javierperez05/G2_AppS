package com.example.cosmos.Model.Event

data class EventItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val paidByUserId: String = "",
    val splitBetweenUserIds: List<String> = emptyList()
)
