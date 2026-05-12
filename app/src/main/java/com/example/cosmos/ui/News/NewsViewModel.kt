package com.example.cosmos.ui.News

import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Actions.Post
import com.example.cosmos.Model.Actions.Rate
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.EventType
import com.example.cosmos.Model.Firestore.Repositories.EventRepository
import com.example.cosmos.Model.Firestore.Repositories.FriendRepository
import com.example.cosmos.Model.Firestore.Repositories.OrbitRepository
import com.example.cosmos.Model.Firestore.Repositories.PostRepository
import com.example.cosmos.Model.Firestore.Repositories.RateRepository
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.Group
import com.example.cosmos.Model.Users.User
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
        val memberNames: Map<String, String>,
        val avatarUrls: Map<String, String>
    ) : NewsUiState()
    data class Error(val message: String) : NewsUiState()
}

sealed class CrewUiState {
    object Idle : CrewUiState()
    data class Ready(
        val eventId: String,
        val memberIds: List<String>,
        val memberNames: Map<String, String>,
        val avatarUrls: Map<String, String>,
        val rates: List<Rate>
    ) : CrewUiState()
}

sealed class ProposeUiState {
    object Idle    : ProposeUiState()
    object Loading : ProposeUiState()
    data class Ready(val post: Post, val groups: List<Group>, val friends: List<User>) : ProposeUiState()
    object Proposed : ProposeUiState()
    data class Error(val message: String) : ProposeUiState()
}

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val orbitRepository: OrbitRepository,
    private val userRepository: UserRepository,
    private val rateRepository: RateRepository,
    private val eventRepository: EventRepository,
    private val friendRepository: FriendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val _crewState = MutableStateFlow<CrewUiState>(CrewUiState.Idle)
    val crewState: StateFlow<CrewUiState> = _crewState.asStateFlow()

    private val _proposeState = MutableStateFlow<ProposeUiState>(ProposeUiState.Idle)
    val proposeState: StateFlow<ProposeUiState> = _proposeState.asStateFlow()

    private val _selectedPost = MutableStateFlow<Post?>(null)
    val selectedPost: StateFlow<Post?> = _selectedPost.asStateFlow()

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

                // 3. Resolve member names + avatars for all memberIds in posts (including authors)
                val postAuthorIds = posts.mapNotNull { it.userId }.toSet()
                val postMemberIds = (posts.flatMap { it.memberIds }.toSet() + postAuthorIds).toList()
                if (postMemberIds.isEmpty()) {
                    allMemberNames = emptyMap()
                    _uiState.value = NewsUiState.Success(posts, emptyMap(), emptyMap())
                    return@getPostsByUserIds
                }

                userRepository.getUsersByIds(postMemberIds) { users ->
                    allMemberNames = users.associate { (it.id ?: "") to (it.username ?: "?") }
                    val avatarUrls = users.associate { (it.id ?: "") to (it.profilePictureUrl ?: "") }
                    _uiState.value = NewsUiState.Success(posts, allMemberNames, avatarUrls)
                }
            }
        }
    }

    fun loadCrewRates(eventId: String, memberIds: List<String>) {
        rateRepository.getRates(eventId) { rates ->
            userRepository.getUsersByIds(memberIds) { users ->
                val names = users.associate { (it.id ?: "") to (it.username ?: "?") }
                val avatars = users.associate { (it.id ?: "") to (it.profilePictureUrl ?: "") }
                _crewState.value = CrewUiState.Ready(eventId, memberIds, names, avatars, rates)
            }
        }
    }

    fun resetCrewState() {
        _crewState.value = CrewUiState.Idle
    }

    // ── Propose ───────────────────────────────────────────────────────────────

    fun loadProposeData(userId: String, post: Post) {
        _proposeState.value = ProposeUiState.Loading
        var groups: List<Group>? = null
        var friends: List<User>? = null

        fun tryEmit() {
            val g = groups ?: return
            val f = friends ?: return
            _proposeState.value = ProposeUiState.Ready(post, g, f)
        }

        orbitRepository.getUserGroups(userId) { result ->
            groups = result
            tryEmit()
        }
        friendRepository.getFriends(userId) { result ->
            friends = result
            tryEmit()
        }
    }

    fun proposeToGroup(post: Post, group: Group, currentUserId: String) {
        _proposeState.value = ProposeUiState.Loading
        val event = Event(
            title       = post.eventTitle,
            description = post.eventDescription ?: post.comment,
            location    = post.eventLocation,
            type        = EventType.DEFAULT,
            adminIds    = listOf(currentUserId),
            memberIds   = group.memberIds.ifEmpty { listOf(currentUserId) }
        )
        eventRepository.createEventGetId(event) { eventId ->
            if (eventId == null) {
                _proposeState.value = ProposeUiState.Error("Error al crear la misión")
                return@createEventGetId
            }
            orbitRepository.addEventToGroup(group.id ?: "", eventId) { success ->
                _proposeState.value = if (success) ProposeUiState.Proposed
                else ProposeUiState.Error("Error al vincular a la órbita")
            }
        }
    }

    fun proposeToFriend(post: Post, friend: User, currentUserId: String) {
        _proposeState.value = ProposeUiState.Loading
        val event = Event(
            title       = post.eventTitle,
            description = post.eventDescription ?: post.comment,
            location    = post.eventLocation,
            type        = EventType.DEFAULT,
            adminIds    = listOf(currentUserId),
            memberIds   = listOf(currentUserId, friend.id ?: "").filter { it.isNotEmpty() }
        )
        eventRepository.createEventGetId(event) { eventId ->
            _proposeState.value = if (eventId != null) ProposeUiState.Proposed
            else ProposeUiState.Error("Error al proponer la misión")
        }
    }

    fun resetProposeState() {
        _proposeState.value = ProposeUiState.Idle
    }

    fun selectPost(post: Post) {
        _selectedPost.value = post
    }

    fun clearSelectedPost() {
        _selectedPost.value = null
    }
}
