package com.example.cosmos.Model.Users

import com.example.cosmos.Model.Actions.Rate
import com.example.cosmos.Model.Event.Event

data class User(
    val id: String? = null,
    val username: String? = null,
    val usernameLower: String? = null,
    val email: String? = null,
    val password: String? = null,
    val profilePictureUrl: String? = null,
    val profilePictureBase64: String? = null,
    val config: UserConfig = UserConfig(),
    val friends: List<String> = emptyList() // Guardamos IDs para evitar recursión
)