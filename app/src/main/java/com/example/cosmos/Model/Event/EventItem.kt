package com.example.cosmos.Model.Event

import com.example.cosmos.Model.Users.User

class EventItem(
    id: String? = null,
    name: String? = null,
    price : Double? = null,
    count : Number? = null,
    responsability : List<User> = emptyList(),
) {
}