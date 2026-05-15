package com.example.cosmos.Model.Event

import com.example.cosmos.Model.Users.User
import com.google.type.DateTime
import java.util.Date

data class Event(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val date: Date? = null,
    val location: String? = null,
    val mapAddress: String? = null,
    val imageURL: String? = null,
    val durationMinutes: Int? = null,
    val items: List<EventItem> = emptyList(),
    val adminIds: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(),
    val pendingIds: List<String> = emptyList(),
    val type: EventType = EventType.DEFAULT,
    val finished: Boolean = false
)