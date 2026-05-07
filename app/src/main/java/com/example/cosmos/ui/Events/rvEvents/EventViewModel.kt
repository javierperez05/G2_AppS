package com.example.cosmos.ui.Events.rvEvents

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Actions.FriendRequest
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Firestore.Repositories.EventRepository
import com.example.cosmos.Model.Firestore.Repositories.FriendRequestRepository
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
    data class Success(val events: List<Event>) : EventUiState()
    data class Error(val message: String)       : EventUiState()
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
    private val friendRequestRepository: FriendRequestRepository
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

    // ── Lista de eventos ──────────────────────────────────────────────────────

    fun loadEvents(userId: String) {
        if (userId.isEmpty()) {
            _uiState.value = EventUiState.Error("Usuario no identificado")
            return
        }
        eventRepository.getUserEvents(userId) { events ->
            _uiState.value = if (events.isEmpty()) EventUiState.Empty
            else EventUiState.Success(events)
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

    fun createEvent(event: Event) {
        _createState.value = CreateEventUiState.Loading
        eventRepository.createEvent(event) { success ->
            _createState.value = if (success) CreateEventUiState.Success(event.id ?: "")
            else CreateEventUiState.Error("Error al lanzar el evento")
        }
    }

    fun resetCreateState() { _createState.value = CreateEventUiState.Idle }
}