package com.example.cosmos.ui.Events.Detail

import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Actions.Post
import com.example.cosmos.Model.Actions.Rate
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.ForumReply
import com.example.cosmos.Model.Event.ForumThread
import com.example.cosmos.Model.Firestore.Repositories.EventRepository
import com.example.cosmos.Model.Firestore.Repositories.ForumRepository
import com.example.cosmos.Model.Firestore.Repositories.PostRepository
import com.example.cosmos.Model.Firestore.Repositories.RateRepository
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
        val memberNames: Map<String, String>,
        val canFinish: Boolean = false,
        val hasRated: Boolean = false,
        val hasPosted: Boolean = false
    ) : EventDetailUiState()
    data class Error(val message: String) : EventDetailUiState()
}

sealed class RateUiState {
    object Idle : RateUiState()
    object Success : RateUiState()
    data class Error(val message: String) : RateUiState()
}

sealed class PostUiState {
    object Idle : PostUiState()
    object Success : PostUiState()
    data class Error(val message: String) : PostUiState()
}

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val forumRepository: ForumRepository,
    private val userRepository: UserRepository,
    private val rateRepository: RateRepository,
    private val postRepository: PostRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EventDetailUiState>(EventDetailUiState.Loading)
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    private val _rateState = MutableStateFlow<RateUiState>(RateUiState.Idle)
    val rateState: StateFlow<RateUiState> = _rateState.asStateFlow()

    private val _postState = MutableStateFlow<PostUiState>(PostUiState.Idle)
    val postState: StateFlow<PostUiState> = _postState.asStateFlow()

    private var memberNames: Map<String, String> = emptyMap()
    private var threadsListener: ListenerRegistration? = null
    private var currentEvent: Event? = null
    private var currentUserId: String = ""

    fun loadEvent(eventId: String, userId: String = "") {
        _uiState.value = EventDetailUiState.Loading
        if (userId.isNotEmpty()) currentUserId = userId
        eventRepository.getEventById(eventId) { event ->
            if (event == null) {
                _uiState.value = EventDetailUiState.Error("Evento no encontrado")
                return@getEventById
            }
            currentEvent = event
            userRepository.getUsersByIds(event.memberIds) { users ->
                memberNames = users.associate { (it.id ?: "") to (it.username ?: "?") }

                // Comprobar si el usuario ya ha valorado y posteado
                rateRepository.getRates(eventId) { rates ->
                    val hasRated = rates.any { it.userId == currentUserId }
                    val canFinish = canFinishEvent(event)

                    postRepository.hasPosted(currentUserId, eventId) { hasPosted ->
                        threadsListener?.remove()
                        threadsListener = forumRepository.listenThreads(eventId) { threads ->
                            _uiState.value = EventDetailUiState.Success(
                                event, threads, memberNames, canFinish, hasRated, hasPosted
                            )
                        }
                    }
                }
            }
        }
    }

    private fun canFinishEvent(event: Event): Boolean {
        if (event.finished) return false
        val start = event.date?.time ?: return false
        val duration = event.durationMinutes ?: return false
        val endTime = start + duration * 60_000L
        return System.currentTimeMillis() >= endTime
    }

    fun finishEvent(eventId: String) {
        eventRepository.finishEvent(eventId) { success ->
            if (success) {
                currentEvent = currentEvent?.copy(finished = true)
                val current = _uiState.value
                if (current is EventDetailUiState.Success) {
                    _uiState.value = current.copy(
                        event = current.event.copy(finished = true),
                        canFinish = false
                    )
                }
            }
        }
    }

    fun submitRate(eventId: String, userId: String, rating: Float, comment: String) {
        val rate = Rate(
            userId = userId,
            eventId = eventId,
            rating = rating,
            comment = comment.ifBlank { null }
        )
        rateRepository.postRate(eventId, rate) { success ->
            if (success) {
                _rateState.value = RateUiState.Success
                // Actualizar hasRated en el state
                val current = _uiState.value
                if (current is EventDetailUiState.Success) {
                    _uiState.value = current.copy(hasRated = true)
                }
            } else {
                _rateState.value = RateUiState.Error("Error al enviar valoracion")
            }
        }
    }

    fun resetRateState() { _rateState.value = RateUiState.Idle }
    fun resetPostState() { _postState.value = PostUiState.Idle }

    fun publishPost(eventId: String, userId: String) {
        val event = currentEvent ?: return
        val username = memberNames[userId] ?: userId

        // Find user's rate for the comment/rating
        rateRepository.getRates(eventId) { rates ->
            val userRate = rates.find { it.userId == userId }
            val post = Post(
                userId = userId,
                username = username,
                eventId = eventId,
                eventTitle = event.title,
                eventDescription = event.description,
                eventLocation = event.location,
                rating = userRate?.rating ?: 0f,
                comment = userRate?.comment,
                memberIds = event.memberIds,
                createdAt = System.currentTimeMillis()
            )
            postRepository.createPost(post) { success ->
                if (success) {
                    _postState.value = PostUiState.Success
                    val current = _uiState.value
                    if (current is EventDetailUiState.Success) {
                        _uiState.value = current.copy(hasPosted = true)
                    }
                } else {
                    _postState.value = PostUiState.Error("Error al publicar")
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
