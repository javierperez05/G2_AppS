package com.example.cosmos.ui.News

import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Actions.Post
import com.example.cosmos.Model.Actions.Rate
import com.example.cosmos.Model.Firestore.Repositories.OrbitRepository
import com.example.cosmos.Model.Firestore.Repositories.PostRepository
import com.example.cosmos.Model.Firestore.Repositories.RateRepository
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed class NewsUiState {
    object Loading : NewsUiState()
    object Empty : NewsUiState()
    data class Success(
        val posts: List<Post>,
        val memberNames: Map<String, String>
    ) : NewsUiState()
    data class Error(val message: String) : NewsUiState()
}

sealed class CrewUiState {
    object Idle : CrewUiState()
    data class Ready(
        val eventId: String,
        val memberIds: List<String>,
        val memberNames: Map<String, String>,
        val rates: List<Rate>
    ) : CrewUiState()
}

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val orbitRepository: OrbitRepository,
    private val userRepository: UserRepository,
    private val rateRepository: RateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val _crewState = MutableStateFlow<CrewUiState>(CrewUiState.Idle)
    val crewState: StateFlow<CrewUiState> = _crewState.asStateFlow()

    private var allMemberNames: Map<String, String> = emptyMap()

    fun loadNews(userId: String) {
        if (userId.isEmpty()) { _uiState.value = NewsUiState.Error("Usuario no identificado"); return }
        _uiState.value = NewsUiState.Loading

        // 1. Get user's orbits to find all relevant user IDs
        orbitRepository.getUserGroups(userId) { groups ->
            val allMemberIds = groups.flatMap { it.memberIds }.toMutableSet()
            allMemberIds.add(userId)
            val uniqueIds = allMemberIds.toList()

            // 2. Query posts by all orbit member IDs + own
            postRepository.getPostsByUserIds(uniqueIds) { posts ->
                if (posts.isEmpty()) {
                    _uiState.value = NewsUiState.Empty
                    return@getPostsByUserIds
                }

                // 3. Resolve member names for all memberIds in posts
                val postMemberIds = posts.flatMap { it.memberIds }.toSet().toList()
                if (postMemberIds.isEmpty()) {
                    allMemberNames = emptyMap()
                    _uiState.value = NewsUiState.Success(posts, emptyMap())
                    return@getPostsByUserIds
                }

                userRepository.getUsersByIds(postMemberIds) { users ->
                    allMemberNames = users.associate { (it.id ?: "") to (it.username ?: "?") }
                    _uiState.value = NewsUiState.Success(posts, allMemberNames)
                }
            }
        }
    }

    fun loadCrewRates(eventId: String, memberIds: List<String>) {
        rateRepository.getRates(eventId) { rates ->
            userRepository.getUsersByIds(memberIds) { users ->
                val names = users.associate { (it.id ?: "") to (it.username ?: "?") }
                _crewState.value = CrewUiState.Ready(eventId, memberIds, names, rates)
            }
        }
    }

    fun resetCrewState() {
        _crewState.value = CrewUiState.Idle
    }
}
