package com.example.cosmos.Model.Actions

import com.example.cosmos.Model.Users.User
import com.google.type.DateTime
import java.util.Date

data class Notification(
    val id: String? = null,
    val targetUserIds: List<String> = emptyList(),
    val imageUrl: String? = null,
    val message: String? = null,
    val createdAt: Date = Date()
)