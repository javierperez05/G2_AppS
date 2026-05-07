package com.example.cosmos.ui.Profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Firestore.Repositories.EventRepository
import com.example.cosmos.Model.Firestore.Repositories.OrbitRepository
import com.example.cosmos.Model.Firestore.Repositories.StorageRepository
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(
        val user: User,
        val events: List<Event>,
        val orbitCount: Int
    ) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

sealed class AvatarState {
    object Idle : AvatarState()
    object Uploading : AvatarState()
    data class Success(val url: String) : AvatarState()
    data class Error(val message: String) : AvatarState()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val orbitRepository: OrbitRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _avatarState = MutableStateFlow<AvatarState>(AvatarState.Idle)
    val avatarState: StateFlow<AvatarState> = _avatarState.asStateFlow()

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var cachedUser: User? = null
    private var cachedEvents: List<Event>? = null
    private var cachedOrbitCount: Int? = null

    fun loadProfile(userId: String) {
        if (userId.isEmpty()) {
            _uiState.value = ProfileUiState.Error("Usuario no identificado")
            return
        }
        _uiState.value = ProfileUiState.Loading
        cachedUser = null
        cachedEvents = null
        cachedOrbitCount = null

        userRepository.getUserById(userId) { user ->
            cachedUser = user
            tryEmit()
        }

        eventRepository.getUserEvents(userId) { events ->
            cachedEvents = events
            tryEmit()
        }

        orbitRepository.getUserGroups(userId) { groups ->
            cachedOrbitCount = groups.size
            tryEmit()
        }
    }

    private fun tryEmit() {
        val user = cachedUser ?: return
        val events = cachedEvents ?: return
        val orbitCount = cachedOrbitCount ?: return
        _uiState.value = ProfileUiState.Success(user, events, orbitCount)
    }

    fun uploadAvatar(userId: String, imageUri: Uri) {
        _avatarState.value = AvatarState.Uploading
        storageRepository.uploadAvatar(userId, imageUri) { url ->
            if (url != null) {
                // Guardar URL en Firestore
                userRepository.updateUserFields(userId, mapOf("profilePictureUrl" to url)) { success ->
                    if (success) {
                        _avatarState.value = AvatarState.Success(url)
                    } else {
                        _avatarState.value = AvatarState.Error("Error al guardar avatar")
                    }
                }
            } else {
                _avatarState.value = AvatarState.Error("Error al subir imagen")
            }
        }
    }

    fun resetAvatarState() { _avatarState.value = AvatarState.Idle }
}
