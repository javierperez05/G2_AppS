package com.example.cosmos.Model.Event

import com.example.cosmos.Model.Users.User
import com.google.type.DateTime

class Event (
    id: String? = null,
    title: String? = null,
    description: String? = null,
    date: DateTime? = null,
    location: String? = null,
    imageUrl: String? = null,
    duration : String? = null,
    admins : List<User> = emptyList(),
    members : List<User> = emptyList(),
    type : EventType = EventType.DEFAULT,
){
}