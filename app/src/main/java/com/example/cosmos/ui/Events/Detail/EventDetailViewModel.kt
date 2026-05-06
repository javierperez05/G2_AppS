package com.example.cosmos.ui.Events.Detail

import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.ForumReply
import com.example.cosmos.Model.Event.ForumThread
import com.example.cosmos.Model.Firestore.Repositories.EventRepository
import com.example.cosmos.Model.Firestore.Repositories.ForumRepository
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed class EventDetailUiState {
    object Loading : EventDetailUiState()
    data class Success(
        val event: Event,
        val threads: List<ForumThread>,
        val memberNames: Map<String, String>
    ) : EventDetailUiState()
    data class Error(val message: String) : EventDetailUiState()
}

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val forumRepository: ForumRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EventDetailUiState>(EventDetailUiState.Loading)
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    private var memberNames: Map<String, String> = emptyMap()
    private var threadsListener: ListenerRegistration? = null

    fun loadEvent(eventId: String) {
        _uiState.value = EventDetailUiState.Loading
        eventRepository.getEventById(eventId) { event ->
            if (event == null) {
                _uiState.value = EventDetailUiState.Error("Evento no encontrado")
                return@getEventById
            }
            userRepository.getUsersByIds(event.memberIds) { users ->
                memberNames = users.associate { (it.id ?: "") to (it.username ?: "?") }
                threadsListener?.remove()
                threadsListener = forumRepository.listenThreads(eventId) { threads ->
                    _uiState.value = EventDetailUiState.Success(event, threads, memberNames)
                }
            }
        }
    }

    fun postQuestion(eventId: String, authorId: String, text: String) {
        if (text.isBlank()) return
        val thread = ForumThread(
            authorId = authorId,
            authorName = memberNames[authorId] ?: authorId,
            text = text.trim(),
            createdAt = System.currentTimeMillis()
        )
        forumRepository.postThread(eventId, thread) {}
    }

    fun postReply(eventId: String, threadId: String, authorId: String, text: String) {
        if (text.isBlank()) return
        val reply = ForumReply(
            authorId = authorId,
            authorName = memberNames[authorId] ?: authorId,
            text = text.trim(),
            createdAt = System.currentTimeMillis()
        )
        forumRepository.postReply(eventId, threadId, reply) {}
    }

    override fun onCleared() {
        super.onCleared()
        threadsListener?.remove()
    }
}
