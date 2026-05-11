package com.example.cosmos.ui.Profile.Config

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  ¿Por qué un ViewModel para Config?
 *      La pantalla de configuración necesita leer datos de Firestore
 *      (el username actual, los switches del UserConfig) y escribir
 *      de vuelta cuando el usuario cambia algo. El ViewModel es el
 *      lugar correcto para esa lógica: el Fragment solo dibuja y
 *      escucha eventos, el ViewModel habla con el repositorio.
 *
 *  ConfigUiState — sealed class
 *      Idle:    estado inicial, no hay nada que mostrar
 *      Loading: cargando datos del usuario
 *      Loaded:  datos listos, Fragment los pinta
 *      Error:   algo falló al cargar
 *      Los estados de guardado (username, config) son separados
 *      porque pueden ocurrir mientras ya estamos en Loaded.
 *
 *  SaveUsernameState — sealed class separada
 *      Manejar el guardado del username por separado permite mostrar
 *      feedback específico en el campo de texto sin interferir con
 *      el estado general de la pantalla.
 *
 *  saveConfig(field, value)
 *      En vez de guardar todo el UserConfig cada vez, usamos
 *      updateUserFields con un mapa de un solo campo. Así si dos
 *      switches se tocan rápido, no se sobreescriben entre sí.
 * ═══════════════════════════════════════════════════════════════════
 */

import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

sealed class ConfigUiState {
    object Idle    : ConfigUiState()
    object Loading : ConfigUiState()
    data class Loaded(val user: User) : ConfigUiState()
    data class Error(val msg: String) : ConfigUiState()
}

sealed class SaveUsernameState {
    object Idle    : SaveUsernameState()
    object Saving  : SaveUsernameState()
    object Success : SaveUsernameState()
    data class Error(val msg: String) : SaveUsernameState()
}

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConfigUiState>(ConfigUiState.Idle)
    val uiState: StateFlow<ConfigUiState> = _uiState

    private val _saveUsernameState = MutableStateFlow<SaveUsernameState>(SaveUsernameState.Idle)
    val saveUsernameState: StateFlow<SaveUsernameState> = _saveUsernameState

    fun loadUser(userId: String) {
        if (userId.isEmpty()) return
        _uiState.value = ConfigUiState.Loading
        userRepository.getUserById(userId) { user ->
            _uiState.value = if (user != null) ConfigUiState.Loaded(user)
                             else ConfigUiState.Error("No se pudo cargar el perfil")
        }
    }

    fun saveUsername(userId: String, newUsername: String) {
        val trimmed = newUsername.trim()
        if (trimmed.isEmpty()) {
            _saveUsernameState.value = SaveUsernameState.Error("El nombre no puede estar vacío")
            return
        }
        _saveUsernameState.value = SaveUsernameState.Saving
        val fields = mapOf(
            "username"      to trimmed,
            "usernameLower" to trimmed.lowercase()
        )
        userRepository.updateUserFields(userId, fields) { success ->
            _saveUsernameState.value = if (success) SaveUsernameState.Success
                                       else SaveUsernameState.Error("Error al guardar")
        }
    }

    fun resetSaveUsernameState() {
        _saveUsernameState.value = SaveUsernameState.Idle
    }

    // Guarda un solo campo del sub-objeto config.
    // Los campos anidados en Firestore se acceden con notación de punto: "config.isPrivate"
    fun saveConfigField(userId: String, field: String, value: Boolean) {
        userRepository.updateUserFields(userId, mapOf("config.$field" to value)) { /* fire and forget */ }
    }
}
