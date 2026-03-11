package com.example.cosmos.Model.Actions

import com.example.cosmos.Model.Users.Group
import com.example.cosmos.Model.Users.User

class Rate (
    id: String? = null,
    userId: String? = null,
    eventId: String? = null,
    rating: Number? = null,
    comment: String? = null,
    images : List<String>? = null,
    groups : List<Group>? = null, // Stats de ratting
    otherUsers : List<User>? = null
) {
}