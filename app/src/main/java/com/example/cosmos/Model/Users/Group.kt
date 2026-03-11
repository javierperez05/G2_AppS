package com.example.cosmos.Model.Users

import com.example.cosmos.Model.Event.Event

class Group (
    id: String? = null,
    name: String? = null,
    description: String? = null,
    members: List<User>? = null,
    events: List<Event>? = null
) {
}