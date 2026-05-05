package com.example.cosmos.ui.Orbit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Firestore.Repositories.EventRepository
import com.example.cosmos.Model.Firestore.Repositories.OrbitRepository
import com.example.cosmos.Model.Users.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GroupsUiState {
    object Loading : GroupsUiState()
    object Empty   : GroupsUiState()
    data class Success(val groups: List<Group>) : GroupsUiState()
    data class Error(val message: String)       : GroupsUiState()
}

sealed class GroupEventsUiState {
    object Loading : GroupEventsUiState()
    object Empty   : GroupEventsUiState()
    data class Success(val events: List<Event>) : GroupEventsUiState()
}

sealed class GroupActionState {
    object Idle    : GroupActionState()
    object Loading : GroupActionState()
    object Success : GroupActionState()
    data class Error(val message: String) : GroupActionState()
}

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val orbitRepository: OrbitRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupsUiState>(GroupsUiState.Loading)
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<GroupActionState>(GroupActionState.Idle)
    val actionState: StateFlow<GroupActionState> = _actionState.asStateFlow()

    private val _selectedGroup = MutableStateFlow<Group?>(null)
    val selectedGroup: StateFlow<Group?> = _selectedGroup.asStateFlow()

    private val _groupEventsState = MutableStateFlow<GroupEventsUiState>(GroupEventsUiState.Loading)
    val groupEventsState: StateFlow<GroupEventsUiState> = _groupEventsState.asStateFlow()

    fun loadGroups(userId: String) {
        if (userId.isEmpty()) {
            _uiState.value = GroupsUiState.Error("Usuario no identificado")
            return
        }
        viewModelScope.launch {
            orbitRepository.getUserGroupsFlow(userId)
                .catch { e ->
                    _uiState.value = GroupsUiState.Error(e.message ?: "Error cargando órbitas")
                }
                .collect { groups ->
                    _uiState.value = if (groups.isEmpty()) GroupsUiState.Empty
                    else GroupsUiState.Success(groups)
                }
        }
    }

    // userId viene del Intent, se pasa desde el Fragment
    fun createGroup(name: String, description: String, userId: String) {
        if (name.isBlank()) {
            _actionState.value = GroupActionState.Error("El nombre no puede estar vacío")
            return
        }
        _actionState.value = GroupActionState.Loading
        val group = Group(
            name        = name,
            description = description,
            memberIds   = listOf(userId),
            adminIds    = listOf(userId)
        )
        orbitRepository.createGroup(group) { success ->
            _actionState.value = if (success) GroupActionState.Success
            else GroupActionState.Error("Error al crear la órbita")
        }
    }

    fun loadGroupEvents(eventIds: List<String>) {
        _groupEventsState.value = GroupEventsUiState.Loading
        eventRepository.getEventsByIds(eventIds) { events ->
            _groupEventsState.value = if (events.isEmpty()) GroupEventsUiState.Empty
            else GroupEventsUiState.Success(events)
        }
    }

    fun selectGroup(group: Group) { _selectedGroup.value = group }
    fun resetActionState() { _actionState.value = GroupActionState.Idle }
}