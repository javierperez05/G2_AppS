package com.example.cosmos.ui.Events.Detail

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  ListenerRegistration
 *      Cuando abres un listener de Firestore en tiempo real
 *      (addSnapshotListener), Firestore empieza a enviarte actualizaciones
 *      indefinidamente hasta que lo canceles explícitamente.
 *      ListenerRegistration es el "ticket" que te devuelve para poder
 *      cancelarlo más tarde con .remove().
 *      Si no lo cancelas, el listener sigue activo aunque el usuario
 *      haya salido de la pantalla → consumo innecesario de red y memoria.
 *
 *  onCleared()
 *      Método del ViewModel que se llama justo antes de que sea destruido
 *      (cuando el Fragment hace popBackStack() definitivamente).
 *      Es el lugar correcto para limpiar listeners de Firestore.
 *
 *  Tres StateFlows independientes
 *      uiState     → estado general de la pantalla (evento + foro + miembros)
 *      rateState   → estado de enviar una valoración
 *      postState   → estado de publicar en News
 *      Se separan para que una acción (ej. enviar rate) no fuerce a
 *      recargar toda la pantalla. Cada uno va a su propio observer.
 *
 *  memberNames: Map<String, String>
 *      Mapa de userId → username. Se carga una vez al abrir el detalle
 *      y se reutiliza para el foro (mostrar quién escribió cada thread)
 *      y para publishPost (incluir el username del autor en el post).
 *
 *  canFinishEvent()
 *      Un evento se puede finalizar solo si:
 *        1. No está ya finalizado
 *        2. La hora actual ha superado la hora de fin (fecha inicio + duración)
 *      Así se evita que alguien finalice un evento antes de que ocurra.
 * ═══════════════════════════════════════════════════════════════════
 */

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

    private val _uiState    = MutableStateFlow<EventDetailUiState>(EventDetailUiState.Loading)
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    private val _rateState  = MutableStateFlow<RateUiState>(RateUiState.Idle)
    val rateState: StateFlow<RateUiState> = _rateState.asStateFlow()

    private val _postState  = MutableStateFlow<PostUiState>(PostUiState.Idle)
    val postState: StateFlow<PostUiState> = _postState.asStateFlow()

    private var memberNames: Map<String, String> = emptyMap()
    // El ticket del listener de Firestore para los threads del foro.
    // Se guarda aquí para poder cancelarlo en onCleared().
    private var threadsListener: ListenerRegistration? = null
    private var currentEvent: Event? = null
    private var currentUserId: String = ""

    // Carga en cadena: primero el evento, luego sus miembros, luego
    // comprueba rates y posts, y finalmente abre el listener de threads.
    // El listener se abre al final para que memberNames ya esté disponible
    // cuando lleguen los threads (los necesitamos para mostrar los nombres).
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
                // Construimos el mapa userId→username para usarlo en todo el ViewModel
                memberNames = users.associate { (it.id ?: "") to (it.username ?: "?") }

                rateRepository.getRates(eventId) { rates ->
                    val hasRated = rates.any { it.userId == currentUserId }
                    val canFinish = canFinishEvent(event)

                    postRepository.hasPosted(currentUserId, eventId) { hasPosted ->
                        // Cancelamos el listener anterior si existía (ej. si loadEvent
                        // se llama dos veces) antes de abrir uno nuevo
                        threadsListener?.remove()
                        // listenThreads usa addSnapshotListener: este callback se llamará
                        // ahora con los threads actuales y CADA VEZ que alguien añada
                        // un thread o respuesta, actualizando la UI en tiempo real.
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

    // El evento se puede finalizar si ya ha pasado su hora de fin.
    // durationMinutes es la duración en minutos. Lo convertimos a ms para sumar a la fecha.
    private fun canFinishEvent(event: Event): Boolean {
        if (event.finished) return false
        val start    = event.date?.time ?: return false
        val duration = event.durationMinutes ?: return false
        val endTime  = start + duration * 60_000L
        return System.currentTimeMillis() >= endTime
    }

    fun finishEvent(eventId: String) {
        eventRepository.finishEvent(eventId) { success ->
            if (success) {
                currentEvent = currentEvent?.copy(finished = true)
                // Actualización optimista: modificamos el estado en memoria
                // para que la UI cambie sin esperar a que Firestore notifique
                val current = _uiState.value
                if (current is EventDetailUiState.Success) {
                    _uiState.value = current.copy(
                        event     = current.event.copy(finished = true),
                        canFinish = false
                    )
                }
            }
        }
    }

    fun submitRate(eventId: String, userId: String, rating: Float, comment: String) {
        val rate = Rate(
            userId  = userId,
            eventId = eventId,
            rating  = rating,
            comment = comment.ifBlank { null }
        )
        rateRepository.postRate(eventId, rate) { success ->
            if (success) {
                _rateState.value = RateUiState.Success
                val current = _uiState.value
                if (current is EventDetailUiState.Success) {
                    _uiState.value = current.copy(hasRated = true)
                }
            } else {
                _rateState.value = RateUiState.Error("Error al enviar valoración")
            }
        }
    }

    // Crea el Post con los datos del evento y la valoración del usuario.
    // memberIds se incluye en el post para que el feed de News pueda filtrar
    // "posts de gente en mis órbitas" usando whereArrayContains.
    fun publishPost(eventId: String, userId: String) {
        val event    = currentEvent ?: return
        val username = memberNames[userId] ?: userId

        rateRepository.getRates(eventId) { rates ->
            val userRate = rates.find { it.userId == userId }
            val post = Post(
                userId           = userId,
                username         = username,
                eventId          = eventId,
                eventTitle       = event.title,
                eventDescription = event.description,
                eventLocation    = event.location,
                rating           = userRate?.rating ?: 0f,
                comment          = userRate?.comment,
                memberIds        = event.memberIds,
                createdAt        = System.currentTimeMillis()
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
            authorId   = authorId,
            authorName = memberNames[authorId] ?: authorId,
            text       = text.trim(),
            createdAt  = System.currentTimeMillis()
        )
        forumRepository.postThread(eventId, thread) {}
    }

    fun postReply(eventId: String, threadId: String, authorId: String, text: String) {
        if (text.isBlank()) return
        val reply = ForumReply(
            authorId   = authorId,
            authorName = memberNames[authorId] ?: authorId,
            text       = text.trim(),
            createdAt  = System.currentTimeMillis()
        )
        forumRepository.postReply(eventId, threadId, reply) {}
    }

    // Punto de limpieza. Se llama cuando el Fragment hace popBackStack()
    // y el ViewModel va a ser destruido. CRÍTICO: sin esto el listener
    // de Firestore seguiría activo y consumiendo recursos en background.
    override fun onCleared() {
        super.onCleared()
        threadsListener?.remove()
    }

    fun resetRateState() { _rateState.value = RateUiState.Idle }
    fun resetPostState() { _postState.value = PostUiState.Idle }
}
