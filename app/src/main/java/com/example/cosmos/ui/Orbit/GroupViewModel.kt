package com.example.cosmos.ui.Groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cosmos.Model.Firestore.Repositories.OrbitRepository
import com.example.cosmos.Model.Users.Group
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GroupsUiState {
    object Loading : GroupsUiState()
    object Empty : GroupsUiState()
    data class Success(val groups: List<Group>) : GroupsUiState()
    data class Error(val message: String) : GroupsUiState()
}

sealed class GroupActionState {
    object Idle : GroupActionState()
    object Loading : GroupActionState()
    data class Success(val groupId: String) : GroupActionState()
    data class Error(val message: String) : GroupActionState()
}

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val orbitRepository: OrbitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupsUiState>(GroupsUiState.Loading)
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<GroupActionState>(GroupActionState.Idle)
    val actionState: StateFlow<GroupActionState> = _actionState.asStateFlow()

    private val _selectedGroup = MutableStateFlow<Group?>(null)
    val selectedGroup: StateFlow<Group?> = _selectedGroup.asStateFlow()

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

    fun createGroup(name: String, description: String) {
        if (name.isBlank()) {
            _actionState.value = GroupActionState.Error("El nombre no puede estar vacío")
            return
        }
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            _actionState.value = GroupActionState.Error("Usuario no autenticado")
            return
        }
        viewModelScope.launch {
            _actionState.value = GroupActionState.Loading
            val group = Group(
                name        = name,
                description = description,
                memberIds   = listOf(userId),
                adminIds    = listOf(userId)
            )
            val result = orbitRepository.createGroup(group)
            _actionState.value = result.fold(
                onSuccess = { id -> GroupActionState.Success(id) },
                onFailure = { e  -> GroupActionState.Error(e.message ?: "Error desconocido") }
            )
        }
    }

    fun selectGroup(group: Group) { _selectedGroup.value = group }
    fun resetActionState() { _actionState.value = GroupActionState.Idle }
}