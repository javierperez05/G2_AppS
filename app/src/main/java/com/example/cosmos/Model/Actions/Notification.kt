package com.example.cosmos.Model.Actions

import com.example.cosmos.Model.Users.User
import com.google.type.DateTime

class Notification(
    id: String? = null,
    users: List<User> = emptyList(),
    image : String? = null,
    message: String? = null,
    date: DateTime? = null
) {
}