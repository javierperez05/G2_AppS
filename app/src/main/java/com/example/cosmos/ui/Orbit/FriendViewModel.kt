package com.example.cosmos.ui.Orbit

import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Firestore.Repositories.FriendRepository
import com.example.cosmos.Model.Users.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed class FriendUiState {
    object Loading : FriendUiState()
    object Empty   : FriendUiState()
    data class Success(val users: List<User>) : FriendUiState()
    data class Error(val message: String)     : FriendUiState()
}

sealed class FriendActionState {
    object Idle    : FriendActionState()
    object Success : FriendActionState()
    data class Error(val message: String) : FriendActionState()
}

@HiltViewModel
class FriendViewModel @Inject constructor(
    private val friendRepository: FriendRepository
) : ViewModel() {

    private val _friendsState = MutableStateFlow<FriendUiState>(FriendUiState.Loading)
    val friendsState: StateFlow<FriendUiState> = _friendsState.asStateFlow()

    private val _actionState = MutableStateFlow<FriendActionState>(FriendActionState.Idle)
    val actionState: StateFlow<FriendActionState> = _actionState.asStateFlow()

    // Modo exploración activo o no
    private val _exploreMode = MutableStateFlow(false)
    val exploreMode: StateFlow<Boolean> = _exploreMode.asStateFlow()

    // Cache de IDs de amigos actuales para saber si mostrar "+ Añadir" o "✓ Amigo"
    private val _friendIds = MutableStateFlow<Set<String>>(emptySet())
    val friendIds: StateFlow<Set<String>> = _friendIds.asStateFlow()

    // ── Cargar amigos ─────────────────────────────────────────────────────────

    fun loadFriends(currentUserId: String) {
        _friendsState.value = FriendUiState.Loading
        friendRepository.getFriends(currentUserId) { users ->
            _friendIds.value = users.mapNotNull { it.id }.toSet()
            _friendsState.value = if (users.isEmpty()) FriendUiState.Empty
                                  else FriendUiState.Success(users)
        }
    }

    // ── Buscar amigos (modo normal) ───────────────────────────────────────────

    fun searchFriends(currentUserId: String, query: String) {
        if (query.isBlank()) { loadFriends(currentUserId); return }
        friendRepository.searchFriends(currentUserId, query) { users ->
            _friendsState.value = if (users.isEmpty()) FriendUiState.Empty
                                  else FriendUiState.Success(users)
        }
    }

    // ── Buscar en toda la app (modo exploración) ──────────────────────────────

    fun searchAllUsers(currentUserId: String, query: String) {
        if (query.isBlank()) { _friendsState.value = FriendUiState.Empty; return }
        friendRepository.searchAllUsers(query, currentUserId) { users ->
            _friendsState.value = if (users.isEmpty()) FriendUiState.Empty
                                  else FriendUiState.Success(users)
        }
    }

    // ── Toggle modo exploración ───────────────────────────────────────────────

    fun toggleExploreMode(currentUserId: String, currentQuery: String) {
        _exploreMode.value = !_exploreMode.value
        if (_exploreMode.value) {
            if (currentQuery.isNotBlank()) searchAllUsers(currentUserId, currentQuery)
            else _friendsState.value = FriendUiState.Empty
        } else {
            loadFriends(currentUserId)
        }
    }

    // ── Añadir amigo ──────────────────────────────────────────────────────────

    fun addFriend(currentUserId: String, friendId: String) {
        friendRepository.addFriend(currentUserId, friendId) { success ->
            if (success) {
                _friendIds.value = _friendIds.value + friendId
                _actionState.value = FriendActionState.Success
            } else {
                _actionState.value = FriendActionState.Error("Error al añadir amigo")
            }
        }
    }

    // ── Eliminar amigo ────────────────────────────────────────────────────────

    fun removeFriend(currentUserId: String, friendId: String) {
        friendRepository.removeFriend(currentUserId, friendId) { success ->
            if (success) {
                _friendIds.value = _friendIds.value - friendId
                _actionState.value = FriendActionState.Success
            } else {
                _actionState.value = FriendActionState.Error("Error al eliminar amigo")
            }
        }
    }

    fun resetActionState() { _actionState.value = FriendActionState.Idle }
}
