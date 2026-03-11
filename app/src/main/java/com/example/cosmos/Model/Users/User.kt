package com.example.cosmos.Model.Users

import com.example.cosmos.Model.Actions.Rate
import com.example.cosmos.Model.Event.Event

class User (
    id: String? = null,
    username: String? = null,
    email: String? = null,
    password: String? = null,
    profilePictureUrl: String? = null,
    events: List<Event>? = null,
    groups: List<Group>? = null,
    config: UserConfig = UserConfig(),
    rattings: List<Rate>? = null, //stats
    friends: List<User>? = null
) {
}