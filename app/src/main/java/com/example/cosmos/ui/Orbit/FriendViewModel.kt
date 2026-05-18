package com.example.cosmos.ui.Orbit

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  FriendUiState vs FriendActionState
 *      FriendUiState: estado de la LISTA (loading/empty/success)
 *      FriendActionState: resultado de una ACCIÓN puntual (enviar
 *      solicitud, aceptar, rechazar, eliminar). Son separados porque
 *      una acción exitosa no cambia el estado de carga de la lista.
 *
 *  Tres caches como StateFlow
 *      - friendIds: IDs de amigos actuales. FriendAdapter los usa
 *        para mostrar el botón correcto (amigo/enviar/pendiente).
 *      - pendingSentIds: IDs de usuarios a los que ya enviamos
 *        solicitud. El botón muestra "Pendiente" en vez de "Enviar".
 *      - incomingRequestMap: fromId -> requestId de solicitudes
 *        entrantes. Se necesita el requestId para aceptar/rechazar.
 *      Los tres se actualizan OPTIMISTAMENTE: al enviar/aceptar,
 *      se actualiza el Set local inmediatamente sin esperar a Firestore.
 *
 *  cachedUsername
 *      Para enviar una solicitud necesitamos el nombre del usuario
 *      actual (fromUsername en FriendRequest). Se carga una sola vez
 *      con loadUsername() y se cachea aquí.
 *
 *  Tres modos de búsqueda
 *      - searchFriends: busca DENTRO de tus amigos (filtro local)
 *      - searchAllUsers: busca en toda la app (Firestore, usernameLower)
 *      - loadIncomingRequests: carga solicitudes pendientes de aceptar
 * ═══════════════════════════════════════════════════════════════════
 */

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
