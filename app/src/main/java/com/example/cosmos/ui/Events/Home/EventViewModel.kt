package com.example.cosmos.ui.Events.Home

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Doble flujo: EventUiState + CreateEventUiState
 *      EventUiState maneja la lista de eventos (Home screen).
 *      CreateEventUiState maneja el formulario de crear/editar evento.
 *      Son independientes porque crear un evento no debe resetear la
 *      lista, y viceversa.
 *
 *  tryEmitEvents() — "zip manual"
 *      loadEvents() lanza DOS queries en paralelo: eventos del usuario
 *      (memberIds) y eventos pendientes de aceptar (pendingIds).
 *      Cada query guarda su resultado en un campo nullable. tryEmit
 *      comprueba si AMBOS ya están listos (?: return si alguno es null)
 *      y solo entonces emite el estado Success. Es un patrón de
 *      sincronización sin coroutines.
 *
 *  avatarUrls y adminNames en Success
 *      Una vez tenemos los eventos, cargamos los User de todos los
 *      memberIds para obtener sus fotos de perfil y nombres. Se
 *      guardan en mapas userId→url y userId→username dentro del
 *      estado, para que el Fragment/Adapter no tenga que hacer más
 *      queries.
 *
 *  selectedMembers (para CreateEventFragment)
 *      Al crear un evento, el usuario busca y añade miembros. La
 *      lista vive en el ViewModel para sobrevivir a rotaciones.
 *      addMember/removeMember usan .update{} que es thread-safe.
 * ═══════════════════════════════════════════════════════════════════
 */

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Actions.FriendRequest
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Firestore.Repositories.CloudinaryRepository
import com.example.cosmos.Model.Firestore.Repositories.EventRepository
import com.example.cosmos.Model.Firestore.Repositories.FriendRequestRepository
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

sealed class EventUiState {
    object Loading : EventUiState()
    object Empty   : EventUiState()
    data class Success(
        val events: List<Event>,
        val pendingEvents: List<Event> = emptyList(),
        val avatarUrls: Map<String, String> = emptyMap(),
        val adminNames: Map<String, String> = emptyMap()
    ) : EventUiState()
    data class Error(val message: String) : EventUiState()
}

sealed class CreateEventUiState {
    object Idle    : CreateEventUiState()
    object Loading : CreateEventUiState()
    data class Success(val eventId: String) : CreateEventUiState()
    data class Error(val message: String)   : CreateEventUiState()
}

@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val friendRequestRepository: FriendRequestRepository,
    private val cloudinaryRepository: CloudinaryRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EventUiState>(EventUiState.Loading)
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    private val _createState = MutableStateFlow<CreateEventUiState>(CreateEventUiState.Idle)
    val createState: StateFlow<CreateEventUiState> = _createState.asStateFlow()

    private val _selectedEvent = MutableStateFlow<Event?>(null)
    val selectedEvent: StateFlow<Event?> = _selectedEvent.asStateFlow()

    private val _selectedMembers = MutableStateFlow<List<User>>(emptyList())
    val selectedMembers: StateFlow<List<User>> = _selectedMembers.asStateFlow()

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri.asStateFlow()

    private val _incomingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val incomingRequests: StateFlow<List<FriendRequest>> = _incomingRequests.asStateFlow()

    private val _currentUserAvatarUrl = MutableStateFlow<String?>(null)
    val currentUserAvatarUrl: StateFlow<String?> = _currentUserAvatarUrl.asStateFlow()

    // ── Lista de eventos ──────────────────────────────────────────────────────

    private var memberEvents: List<Event>? = null
    private var pendingEvents: List<Event>? = null

    fun loadEvents(userId: String) {
        if (userId.isEmpty()) {
            _uiState.value = EventUiState.Error("Usuario no identificado")
            return
        }
        memberEvents = null
        pendingEvents = null

        eventRepository.getUserEvents(userId) { events ->
            memberEvents = events
            tryEmitEvents()
        }
        eventRepository.getPendingEvents(userId) { events ->
            pendingEvents = events
            tryEmitEvents()
        }
    }

    private fun tryEmitEvents() {
        val members = memberEvents ?: return
        val pending = pendingEvents ?: return
        if (members.isEmpty() && pending.isEmpty()) {
            _uiState.value = EventUiState.Empty
            return
        }
        // Cargar avatares de todos los miembros de todos los eventos
        val allMemberIds = (members + pending)
            .flatMap { it.memberIds + it.pendingIds + it.adminIds }
            .distinct()
        if (allMemberIds.isEmpty()) {
            _uiState.value = EventUiState.Success(members, pending)
            return
        }
        userRepository.getUsersByIds(allMemberIds) { users ->
            val avatarUrls = users.mapNotNull { user ->
                val id = user.id ?: return@mapNotNull null
                val value = user.profilePictureUrl.takeUnless { it.isNullOrBlank() }
                    ?: user.profilePictureBase64.takeUnless { it.isNullOrBlank() }
                value?.let { id to it }
            }.toMap()
            val adminNames = users.associate { (it.id ?: "") to (it.username ?: "?") }
            _uiState.value = EventUiState.Success(members, pending, avatarUrls, adminNames)
        }
    }

    fun loadCurrentUserAvatar(userId: String) {
        if (userId.isEmpty()) return
        userRepository.getUserById(userId) { user ->
            // Si existe URL remota, la usamos. Si no, guardamos la Base64 (si hay).
            _currentUserAvatarUrl.value = user?.profilePictureUrl ?: user?.profilePictureBase64
        }
    }



    fun loadIncomingRequests(userId: String) {
        if (userId.isEmpty()) return
        friendRequestRepository.getIncomingRequests(userId) { requests ->
            _incomingRequests.value = requests
        }
    }

    fun selectEvent(event: Event) { _selectedEvent.value = event }

    // ── Miembros ──────────────────────────────────────────────────────────────

    fun addMember(user: User) {
        _selectedMembers.update { current ->
            if (current.any { it.id == user.id }) current else current + user
        }
    }

    fun removeMember(user: User) {
        _selectedMembers.update { current -> current.filter { it.id != user.id } }
    }

    fun setImageUri(uri: Uri?) { _imageUri.value = uri }

    // ── Crear evento ──────────────────────────────────────────────────────────

    fun createEvent(event: Event, imageUri: Uri? = null) {
        _createState.value = CreateEventUiState.Loading
        if (imageUri != null) {
            cloudinaryRepository.uploadImage(imageUri) { url ->
                val eventWithImage = event.copy(imageURL = url)
                eventRepository.createEvent(eventWithImage) { success ->
                    _createState.value = if (success) CreateEventUiState.Success(eventWithImage.id ?: "")
                    else CreateEventUiState.Error("Error al lanzar el evento")
                }
            }
        } else {
            eventRepository.createEvent(event) { success ->
                _createState.value = if (success) CreateEventUiState.Success(event.id ?: "")
                else CreateEventUiState.Error("Error al lanzar el evento")
            }
        }
    }

    // ── Editar evento ─────────────────────────────────────────────────────────

    // Carga el evento a editar + sus usuarios miembros en selectedMembers
    fun loadEventForEdit(eventId: String) {
        eventRepository.getEventById(eventId) { event ->
            if (event == null) return@getEventById
            _selectedEvent.value = event
            userRepository.getUsersByIds(event.memberIds) { users ->
                _selectedMembers.value = users
            }
        }
    }

    fun updateEvent(event: Event, imageUri: Uri? = null) {
        _createState.value = CreateEventUiState.Loading
        if (imageUri != null) {
            cloudinaryRepository.uploadImage(imageUri) { url ->
                val updated = event.copy(imageURL = url)
                eventRepository.updateEvent(updated) { success ->
                    _createState.value = if (success) CreateEventUiState.Success(updated.id ?: "")
                    else CreateEventUiState.Error("Error al actualizar el evento")
                }
            }
        } else {
            eventRepository.updateEvent(event) { success ->
                _createState.value = if (success) CreateEventUiState.Success(event.id ?: "")
                else CreateEventUiState.Error("Error al actualizar el evento")
            }
        }
    }

    fun resetCreateState() { _createState.value = CreateEventUiState.Idle }

    // ── Invitaciones a eventos ─────────────────────────────────────────────

    fun acceptEventInvite(eventId: String, userId: String) {
        eventRepository.acceptEventInvite(eventId, userId) { /* snapshotListener actualiza la lista */ }
    }

    fun rejectEventInvite(eventId: String, userId: String) {
        eventRepository.rejectEventInvite(eventId, userId) { /* snapshotListener actualiza la lista */ }
    }
}