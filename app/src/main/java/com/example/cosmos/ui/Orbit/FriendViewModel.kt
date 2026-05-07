package com.example.cosmos.ui.Orbit

import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Actions.FriendRequest
import com.example.cosmos.Model.Firestore.Repositories.FriendRepository
import com.example.cosmos.Model.Firestore.Repositories.FriendRequestRepository
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
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
    data class RequestSent(val message: String = "Solicitud enviada") : FriendActionState()
    data class RequestAccepted(val message: String = "Solicitud aceptada") : FriendActionState()
}

@HiltViewModel
class FriendViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val friendRequestRepository: FriendRequestRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _friendsState = MutableStateFlow<FriendUiState>(FriendUiState.Loading)
    val friendsState: StateFlow<FriendUiState> = _friendsState.asStateFlow()

    private val _actionState = MutableStateFlow<FriendActionState>(FriendActionState.Idle)
    val actionState: StateFlow<FriendActionState> = _actionState.asStateFlow()

    // Cache de IDs de amigos actuales
    private val _friendIds = MutableStateFlow<Set<String>>(emptySet())
    val friendIds: StateFlow<Set<String>> = _friendIds.asStateFlow()

    // IDs de usuarios a los que ya hemos enviado solicitud
    private val _pendingSentIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingSentIds: StateFlow<Set<String>> = _pendingSentIds.asStateFlow()

    // Incoming requests: userId -> requestId
    private val _incomingRequestMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val incomingRequestMap: StateFlow<Map<String, String>> = _incomingRequestMap.asStateFlow()

    private var cachedUsername: String = ""

    // ── Cargar amigos ─────────────────────────────────────────────────────────

    fun loadFriends(currentUserId: String) {
        _friendsState.value = FriendUiState.Loading
        friendRepository.getFriends(currentUserId) { users ->
            _friendIds.value = users.mapNotNull { it.id }.toSet()
            _friendsState.value = if (users.isEmpty()) FriendUiState.Empty
                                  else FriendUiState.Success(users)
        }
        // Also load pending sent requests
        friendRequestRepository.getSentPendingIds(currentUserId) { ids ->
            _pendingSentIds.value = ids
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

    // ── Buscar en toda la app (modo exploracion) ──────────────────────────────

    fun searchAllUsers(currentUserId: String, query: String) {
        if (query.isBlank()) { _friendsState.value = FriendUiState.Empty; return }
        friendRepository.searchAllUsers(query, currentUserId) { users ->
            _friendsState.value = if (users.isEmpty()) FriendUiState.Empty
                                  else FriendUiState.Success(users)
        }
    }

    // ── Cargar solicitudes entrantes ──────────────────────────────────────────

    fun loadIncomingRequests(currentUserId: String) {
        _friendsState.value = FriendUiState.Loading
        friendRequestRepository.getIncomingRequests(currentUserId) { requests ->
            if (requests.isEmpty()) {
                _incomingRequestMap.value = emptyMap()
                _friendsState.value = FriendUiState.Empty
                return@getIncomingRequests
            }
            // Map fromId -> requestId
            _incomingRequestMap.value = requests.associate { it.fromId to (it.id ?: "") }
            // Load user objects for the request senders
            val fromIds = requests.map { it.fromId }
            userRepository.getUsersByIds(fromIds) { users ->
                _friendsState.value = if (users.isEmpty()) FriendUiState.Empty
                                      else FriendUiState.Success(users)
            }
        }
    }

    // ── Enviar solicitud de amistad ──────────────────────────────────────────

    fun sendFriendRequest(currentUserId: String, targetUserId: String, username: String) {
        val request = FriendRequest(
            fromId = currentUserId,
            toId = targetUserId,
            fromUsername = username
        )
        friendRequestRepository.sendRequest(request) { success ->
            if (success) {
                _pendingSentIds.value = _pendingSentIds.value + targetUserId
                _actionState.value = FriendActionState.RequestSent()
            } else {
                _actionState.value = FriendActionState.Error("Ya existe una solicitud pendiente")
            }
        }
    }

    // ── Aceptar solicitud ───────────────────────────────────────────────────

    fun acceptRequest(requestId: String, fromId: String, currentUserId: String) {
        friendRequestRepository.acceptRequest(requestId, fromId, currentUserId) { success ->
            if (success) {
                _friendIds.value = _friendIds.value + fromId
                _incomingRequestMap.value = _incomingRequestMap.value - fromId
                _actionState.value = FriendActionState.RequestAccepted()
                // Refresh the requests list
                loadIncomingRequests(currentUserId)
            } else {
                _actionState.value = FriendActionState.Error("Error al aceptar solicitud")
            }
        }
    }

    // ── Rechazar solicitud ──────────────────────────────────────────────────

    fun rejectRequest(requestId: String, fromId: String, currentUserId: String) {
        friendRequestRepository.rejectRequest(requestId) { success ->
            if (success) {
                _incomingRequestMap.value = _incomingRequestMap.value - fromId
                _actionState.value = FriendActionState.Success
                loadIncomingRequests(currentUserId)
            } else {
                _actionState.value = FriendActionState.Error("Error al rechazar solicitud")
            }
        }
    }

    // ── Eliminar amigo ──────────────────────────────────────────────────────

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

    fun setUsername(username: String) { cachedUsername = username }
    fun getUsername(): String = cachedUsername

    fun loadUsername(userId: String) {
        if (cachedUsername.isNotEmpty()) return
        userRepository.getUserById(userId) { user ->
            cachedUsername = user?.username ?: ""
        }
    }

    fun resetActionState() { _actionState.value = FriendActionState.Idle }
}
