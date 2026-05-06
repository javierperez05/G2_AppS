package com.example.cosmos.ui.News

import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Actions.Rate
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.EventType
import com.example.cosmos.Model.Firestore.Repositories.EventRepository
import com.example.cosmos.Model.Firestore.Repositories.RateRepository
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
import javax.inject.Inject

data class NewsItem(
    val event: Event,
    val rates: List<Rate>,
    val userRate: Rate?,
    val averageRating: Float
)

sealed class NewsUiState {
    object Loading : NewsUiState()
    object Empty   : NewsUiState()
    data class Success(val items: List<NewsItem>) : NewsUiState()
    data class Error(val message: String)         : NewsUiState()
}

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val rateRepository: RateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    fun loadNews(userId: String) {
        if (userId.isEmpty()) { _uiState.value = NewsUiState.Error("Usuario no identificado"); return }
        _uiState.value = NewsUiState.Loading

        // 1. Obtener IDs de amigos desde el documento del usuario
        userRepository.getUserById(userId) { user ->
            val friendIds = user?.friends ?: emptyList()
            if (friendIds.isEmpty()) { _uiState.value = NewsUiState.Empty; return@getUserById }

            // 2. Consultar eventos donde al menos un amigo es miembro (batches de 10)
            eventRepository.getFriendEvents(friendIds) { allEvents ->
                val now = Date()
                val pastEvents = allEvents.filter { it.date != null && it.date.before(now) }
                if (pastEvents.isEmpty()) { _uiState.value = NewsUiState.Empty; return@getFriendEvents }

                // 3. Cargar rates de cada evento
                buildNewsItems(pastEvents, userId)
            }
        }
    }

    private fun buildNewsItems(events: List<Event>, userId: String) {
        val ratesMap = mutableMapOf<String, List<Rate>>()
        var pending = events.size

        events.forEach { event ->
            rateRepository.getRates(event.id ?: "") { rates ->
                synchronized(ratesMap) {
                    ratesMap[event.id ?: ""] = rates
                    pending--
                    if (pending == 0) emitSuccess(events, ratesMap, userId)
                }
            }
        }
    }

    private fun emitSuccess(events: List<Event>, ratesMap: Map<String, List<Rate>>, userId: String) {
        val items = events
            .sortedByDescending { it.date }
            .map { event ->
                val rates = ratesMap[event.id] ?: emptyList()
                val userRate = rates.find { it.userId == userId }
                val avg = if (rates.isEmpty()) 0f else rates.map { it.rating }.average().toFloat()
                NewsItem(event, rates, userRate, avg)
            }
        _uiState.value = if (items.isEmpty()) NewsUiState.Empty else NewsUiState.Success(items)
    }

    fun submitRate(eventId: String, rate: Rate) {
        rateRepository.postRate(eventId, rate) { success ->
            if (!success) return@postRate
            // Actualizar estado localmente sin re-fetch
            val current = _uiState.value as? NewsUiState.Success ?: return@postRate
            val updated = current.items.map { item ->
                if (item.event.id != eventId) return@map item
                val newRates = item.rates.filter { it.userId != rate.userId } + rate
                val newAvg = newRates.map { it.rating }.average().toFloat()
                item.copy(rates = newRates, userRate = rate, averageRating = newAvg)
            }
            _uiState.value = NewsUiState.Success(updated)
        }
    }
}
