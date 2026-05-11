package com.example.cosmos.Model.Users

data class UserConfig(
    // Si es true, el usuario no aparece en búsquedas de "Explorar"
    val isPrivate: Boolean = true,
    // Muestra el email en la pantalla de perfil pública
    val showEmail: Boolean = false,
    // Recibir notificaciones de nuevas solicitudes de amistad (para FCM futuro)
    val notifyRequests: Boolean = true,
    // Recibir notificaciones de eventos próximos (para FCM futuro)
    val notifyEvents: Boolean = true
)
