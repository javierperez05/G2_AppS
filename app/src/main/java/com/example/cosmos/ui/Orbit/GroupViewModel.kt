package com.example.cosmos.ui.Orbit

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  activityViewModels (por qué este ViewModel es "compartido")
 *      Normalmente cada Fragment tiene su propio ViewModel que muere
 *      cuando el Fragment se destruye. Pero a veces dos Fragments
 *      necesitan compartir datos entre sí.
 *      GroupFragment selecciona una órbita → GroupDetailFragment necesita
 *      saber cuál se seleccionó. Si cada uno tuviera su instancia propia
 *      del ViewModel, no podrían comunicarse.
 *      Con activityViewModels() ambos comparten LA MISMA instancia,
 *      que vive mientras viva la Activity (toda la sesión).
 *
 *  selectedGroup
 *      Actúa como "variable de paso" entre fragments.
 *      GroupFragment llama a selectGroup(group), luego navega.
 *      GroupDetailFragment lee selectedGroup.value al abrirse.
 *
 *  openRequestsSignal
 *      Canal de comunicación entre EventFragment y GroupFragment.
 *      Son pantallas sin relación padre/hijo, así que no pueden
 *      llamarse directamente. EventFragment escribe true en la señal,
 *      GroupFragment la lee y abre el bottom sheet en modo REQUESTS.
 *      consumeOpenRequestsSignal() la pone a false para que no se
 *      dispare de nuevo si GroupFragment se recrea.
 *
 *  sealed class para estados
 *      GroupsUiState     → estado de la lista de órbitas
 *      GroupEventsUiState → estado de los eventos de una órbita
 *      GroupActionState  → estado de crear/modificar una órbita
 *      Se separan porque son operaciones independientes que pueden
 *      ocurrir a la vez sin interferirse.
 * ═══════════════════════════════════════════════════════════════════
 */

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Firestore.Repositories.CloudinaryRepository
import com.example.cosmos.Model.Firestore.Repositories.EventRepository
import com.example.cosmos.Model.Firestore.Repositories.OrbitRepository
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.Group
import com.example.cosmos.Model.Users.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GroupsUiState {
    object Loading : GroupsUiState()
    object Empty   : GroupsUiState()
    data class Success(val groups: List<Group>, val invitedGroupIds: Set<String> = emptySet()) : GroupsUiState()
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

sealed class GroupMembersUiState {
    object Loading : GroupMembersUiState()
    data class Success(
        val members: List<User>,
        val invitedUsers: List<User>
    ) : GroupMembersUiState()
}

sealed class GroupDetailActionState {
    object Idle    : GroupDetailActionState()
    object Loading : GroupDetailActionState()
    data class Success(val message: String = "") : GroupDetailActionState()
    data class Error(val message: String) : GroupDetailActionState()
}

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val orbitRepository: OrbitRepository,
    private val eventRepository: EventRepository,
    private val cloudinaryRepository: CloudinaryRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupsUiState>(GroupsUiState.Loading)
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<GroupActionState>(GroupActionState.Idle)
    val actionState: StateFlow<GroupActionState> = _actionState.asStateFlow()

    // La órbita que el usuario tocó en GroupFragment.
    // GroupDetailFragment la lee para saber qué mostrar.
    private val _selectedGroup = MutableStateFlow<Group?>(null)
    val selectedGroup: StateFlow<Group?> = _selectedGroup.asStateFlow()

    private val _groupEventsState = MutableStateFlow<GroupEventsUiState>(GroupEventsUiState.Loading)
    val groupEventsState: StateFlow<GroupEventsUiState> = _groupEventsState.asStateFlow()

    private val _groupMembersState = MutableStateFlow<GroupMembersUiState>(GroupMembersUiState.Loading)
    val groupMembersState: StateFlow<GroupMembersUiState> = _groupMembersState.asStateFlow()

    private val _detailActionState = MutableStateFlow<GroupDetailActionState>(GroupDetailActionState.Idle)
    val detailActionState: StateFlow<GroupDetailActionState> = _detailActionState.asStateFlow()

    // Señal para abrir el bottom sheet de solicitudes desde EventFragment.
    // true = "oye GroupFragment, abre el sheet en modo REQUESTS"
    // false = estado por defecto / ya consumido
    private val _openRequestsSignal = MutableStateFlow(false)
    val openRequestsSignal: StateFlow<Boolean> = _openRequestsSignal.asStateFlow()

    // getUserGroupsFlow devuelve un Flow de Firestore (tiempo real).
    // catch { } maneja errores del Flow sin que rompa el colector.
    // collect { } se llama cada vez que Firestore actualiza la colección.
    fun loadGroups(userId: String) {
        if (userId.isEmpty()) {
            _uiState.value = GroupsUiState.Error("Usuario no identificado")
            return
        }
        viewModelScope.launch {
            combine(
                orbitRepository.getUserGroupsFlow(userId),
                orbitRepository.getInvitedGroupsFlow(userId)
            ) { memberGroups, invitedGroups ->
                val invitedIds = invitedGroups.mapNotNull { it.id }.toSet()
                val allGroups = (memberGroups + invitedGroups).distinctBy { it.id }
                Pair(allGroups, invitedIds)
            }
                .catch { e ->
                    _uiState.value = GroupsUiState.Error(e.message ?: "Error cargando órbitas")
                }
                .collect { (allGroups, invitedIds) ->
                    _uiState.value = if (allGroups.isEmpty()) GroupsUiState.Empty
                    else GroupsUiState.Success(allGroups, invitedIds)
                }
        }
    }

    fun createGroup(
        name: String,
        description: String,
        userId: String,
        memberIds: List<String> = listOf(userId),
        imageUri: Uri? = null
    ) {
        if (name.isBlank()) {
            _actionState.value = GroupActionState.Error("El nombre no puede estar vacío")
            return
        }
        _actionState.value = GroupActionState.Loading
        val allMembers = (memberIds + userId).distinct()

        fun saveWithImage(imageUrl: String?) {
            val group = Group(
                name        = name,
                description = description,
                imageUrl    = imageUrl,
                memberIds   = allMembers,
                adminIds    = listOf(userId)
            )
            orbitRepository.createGroup(group) { success ->
                _actionState.value = if (success) GroupActionState.Success
                else GroupActionState.Error("Error al crear la órbita")
            }
        }

        if (imageUri != null) {
            cloudinaryRepository.uploadImage(imageUri) { url -> saveWithImage(url) }
        } else {
            saveWithImage(null)
        }
    }

    fun loadGroupEvents(eventIds: List<String>) {
        _groupEventsState.value = GroupEventsUiState.Loading
        eventRepository.getEventsByIds(eventIds) { events ->
            _groupEventsState.value = if (events.isEmpty()) GroupEventsUiState.Empty
            else GroupEventsUiState.Success(events)
        }
    }

    fun loadGroupMembers(memberIds: List<String>, invitedIds: List<String>) {
        _groupMembersState.value = GroupMembersUiState.Loading
        val allIds = (memberIds + invitedIds).distinct()
        if (allIds.isEmpty()) {
            _groupMembersState.value = GroupMembersUiState.Success(emptyList(), emptyList())
            return
        }
        userRepository.getUsersByIds(allIds) { users ->
            val members = users.filter { memberIds.contains(it.id) }
            val invited = users.filter { invitedIds.contains(it.id) }
            _groupMembersState.value = GroupMembersUiState.Success(members, invited)
        }
    }

    fun inviteToGroup(groupId: String, userId: String) {
        orbitRepository.inviteUserToGroup(groupId, userId) { success ->
            _detailActionState.value = if (success)
                GroupDetailActionState.Success("Invitación enviada")
            else GroupDetailActionState.Error("Error al enviar invitación")
        }
    }

    fun acceptGroupInvite(groupId: String, userId: String) {
        _detailActionState.value = GroupDetailActionState.Loading
        orbitRepository.acceptGroupInvite(groupId, userId) { success ->
            _detailActionState.value = if (success)
                GroupDetailActionState.Success("Te has unido a la órbita")
            else GroupDetailActionState.Error("Error al aceptar invitación")
        }
    }

    fun rejectGroupInvite(groupId: String, userId: String) {
        orbitRepository.rejectGroupInvite(groupId, userId) { success ->
            _detailActionState.value = if (success)
                GroupDetailActionState.Success()
            else GroupDetailActionState.Error("Error al rechazar invitación")
        }
    }

    fun leaveGroup(groupId: String, userId: String) {
        _detailActionState.value = GroupDetailActionState.Loading
        orbitRepository.leaveGroup(groupId, userId) { success ->
            _detailActionState.value = if (success)
                GroupDetailActionState.Success("Has salido de la órbita")
            else GroupDetailActionState.Error("Error al salir del grupo")
        }
    }

    fun kickMember(groupId: String, userId: String) {
        orbitRepository.removeMemberFromGroup(groupId, userId) { success ->
            _detailActionState.value = if (success)
                GroupDetailActionState.Success("Miembro expulsado")
            else GroupDetailActionState.Error("Error al expulsar miembro")
        }
    }

    fun linkEventToGroup(groupId: String, eventId: String) {
        orbitRepository.addEventToGroup(groupId, eventId) {}
    }

    fun resetDetailActionState() { _detailActionState.value = GroupDetailActionState.Idle }

    // EventFragment llama a signalOpenRequests() cuando el usuario toca
    // una alerta de solicitud de amistad. GroupFragment lo detecta y abre
    // el bottom sheet directamente en modo REQUESTS.
    fun signalOpenRequests() { _openRequestsSignal.value = true }
    // Después de procesar la señal hay que resetearla a false.
    // Si no, cada vez que GroupFragment se recrea (ej. rotación) volvería
    // a abrir el sheet aunque el usuario no haya tocado nada.
    fun consumeOpenRequestsSignal() { _openRequestsSignal.value = false }

    fun selectGroup(group: Group) { _selectedGroup.value = group }
    fun resetActionState() { _actionState.value = GroupActionState.Idle }
}
