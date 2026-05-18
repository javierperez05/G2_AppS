package com.example.cosmos.ui.Orbit

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cosmos.Model.Users.Group
import com.example.cosmos.Model.Users.User
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentGroupBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GroupFragment : Fragment() {

    private var _binding: FragmentGroupBinding? = null
    private val binding get() = _binding!!

    private val groupViewModel: GroupViewModel by activityViewModels()
    private val friendViewModel: FriendViewModel by viewModels()

    private var currentUserId = ""
    private var allGroups = listOf<Group>()

    private val groupAdapter = GroupAdapter { group ->
        groupViewModel.selectGroup(group)
        findNavController().navigate(R.id.action_groupFragment_to_groupDetailFragment)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentUserId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        initUI()
        initListeners()
        observeViewModel()
        groupViewModel.loadGroups(currentUserId)
        friendViewModel.loadUsername(currentUserId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initUI() {
        binding.rvGroups.apply {
            layoutManager = OrbitalLayoutManager(requireContext())
            adapter = groupAdapter
            itemAnimator = null
        }
    }

    private fun initListeners() {
        binding.fabNewGroup.setOnClickListener {
            findNavController().navigate(R.id.action_groupFragment_to_createGroupFragment)
        }
        binding.btnGroups.setOnClickListener { showGroupsBottomSheet() }
        binding.btnFriends.setOnClickListener {
            friendViewModel.loadFriends(currentUserId)
            showFriendsBottomSheet()
        }

        binding.etSearchGroup.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                groupAdapter.submitList(
                    if (query.isEmpty()) allGroups
                    else allGroups.filter {
                        it.name?.contains(query, ignoreCase = true) == true
                    }
                )
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    groupViewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state is GroupsUiState.Loading
                        binding.layoutEmpty.isVisible  = state is GroupsUiState.Empty
                        binding.rvGroups.isVisible     = state is GroupsUiState.Success
                        if (state is GroupsUiState.Success) {
                            allGroups = state.groups
                            groupAdapter.invitedGroupIds = state.invitedGroupIds
                            groupAdapter.submitList(state.groups)
                        }
                    }
                }
                launch {
                    groupViewModel.actionState.collect { state ->
                        when (state) {
                            is GroupActionState.Success -> groupViewModel.resetActionState()
                            is GroupActionState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                groupViewModel.resetActionState()
                            }
                            else -> Unit
                        }
                    }
                }
                launch {
                    groupViewModel.openRequestsSignal.collect { shouldOpen ->
                        if (shouldOpen) {
                            groupViewModel.consumeOpenRequestsSignal()
                            showFriendsBottomSheet(startMode = FriendSheetMode.REQUESTS)
                        }
                    }
                }
            }
        }
    }

    // ── BottomSheet amigos ────────────────────────────────────────────────────

    private enum class FriendSheetMode { FRIENDS, EXPLORE, REQUESTS }

    private fun showFriendsBottomSheet(startMode: FriendSheetMode = FriendSheetMode.FRIENDS) {
        val dialog    = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottomsheet_friends, null)
        dialog.setContentView(sheetView)

        val etSearch     = sheetView.findViewById<EditText>(R.id.etSearchFriend)
        val chipExplore  = sheetView.findViewById<TextView>(R.id.chipExplore)
        val chipRequests = sheetView.findViewById<TextView>(R.id.chipRequests)
        val tvLabel      = sheetView.findViewById<TextView>(R.id.tvFriendsLabel)
        val tvEmpty      = sheetView.findViewById<TextView>(R.id.tvFriendsEmpty)
        val rvFriends    = sheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvFriends)

        var currentMode = FriendSheetMode.FRIENDS
        var fullRequestList = listOf<User>()

        val friendAdapter = FriendAdapter(
            onAddClick = { user ->
                friendViewModel.sendFriendRequest(
                    currentUserId, user.id ?: return@FriendAdapter,
                    friendViewModel.getUsername()
                )
            },
            onRemoveClick = { user ->
                friendViewModel.removeFriend(currentUserId, user.id ?: return@FriendAdapter)
            },
            onAcceptClick = { user, requestId ->
                friendViewModel.acceptRequest(requestId, user.id ?: return@FriendAdapter, currentUserId)
            },
            onRejectClick = { user, requestId ->
                friendViewModel.rejectRequest(requestId, user.id ?: return@FriendAdapter, currentUserId)
            }
        )

        rvFriends.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = friendAdapter
        }

        fun updateChipStyles() {
            chipExplore.setBackgroundResource(R.drawable.bg_chip_glass)
            chipExplore.setTextColor(0x88FFFFFF.toInt())
            chipRequests.setBackgroundResource(R.drawable.bg_chip_glass)
            chipRequests.setTextColor(0x88FFFFFF.toInt())
            when (currentMode) {
                FriendSheetMode.EXPLORE -> {
                    chipExplore.setBackgroundResource(R.drawable.bg_tab_selected)
                    chipExplore.setTextColor(0xFFC4BCFF.toInt())
                }
                FriendSheetMode.REQUESTS -> {
                    chipRequests.setBackgroundResource(R.drawable.bg_tab_selected)
                    chipRequests.setTextColor(0xFFC4BCFF.toInt())
                }
                else -> {}
            }
        }

        fun applyRequestFilter(query: String) {
            val filtered = if (query.isEmpty()) fullRequestList
            else fullRequestList.filter { it.username?.contains(query, ignoreCase = true) == true }
            friendAdapter.submitList(filtered)
            tvEmpty.isVisible = filtered.isEmpty()
            rvFriends.isVisible = filtered.isNotEmpty()
            if (filtered.isEmpty()) tvEmpty.text = getString(R.string.no_pending_requests)
        }

        fun switchMode(mode: FriendSheetMode) {
            if (currentMode == mode && mode != FriendSheetMode.FRIENDS) {
                currentMode = FriendSheetMode.FRIENDS
            } else {
                currentMode = mode
            }
            friendAdapter.isRequestMode = currentMode == FriendSheetMode.REQUESTS
            updateChipStyles()

            when (currentMode) {
                FriendSheetMode.FRIENDS -> {
                    tvLabel.text = getString(R.string.label_your_friends)
                    etSearch.hint = getString(R.string.hint_search)
                    friendViewModel.loadFriends(currentUserId)
                }
                FriendSheetMode.EXPLORE -> {
                    tvLabel.text = getString(R.string.exploring_cosmos)
                    etSearch.hint = getString(R.string.hint_search_users)
                    val query = etSearch.text.toString().trim()
                    friendViewModel.searchAllUsers(currentUserId, query)
                }
                FriendSheetMode.REQUESTS -> {
                    tvLabel.text = getString(R.string.pending_requests)
                    etSearch.hint = getString(R.string.hint_filter_requests)
                    friendViewModel.loadIncomingRequests(currentUserId)
                }
            }
        }

        // Observe friend list state
        viewLifecycleOwner.lifecycleScope.launch {
            friendViewModel.friendsState.collect { state ->
                when (state) {
                    is FriendUiState.Success -> {
                        if (currentMode == FriendSheetMode.REQUESTS) {
                            fullRequestList = state.users
                            applyRequestFilter(etSearch.text.toString().trim())
                        } else {
                            tvEmpty.isVisible = false
                            rvFriends.isVisible = true
                            friendAdapter.submitList(state.users)
                        }
                    }
                    is FriendUiState.Empty -> {
                        if (currentMode == FriendSheetMode.REQUESTS) {
                            fullRequestList = emptyList()
                        }
                        tvEmpty.isVisible = true
                        rvFriends.isVisible = false
                        tvEmpty.text = when (currentMode) {
                            FriendSheetMode.REQUESTS -> "Sin solicitudes pendientes"
                            FriendSheetMode.EXPLORE  -> "Sin resultados"
                            else                     -> "Sin amigos todavia"
                        }
                    }
                    else -> Unit
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            friendViewModel.friendIds.collect { ids -> friendAdapter.friendIds = ids }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            friendViewModel.pendingSentIds.collect { ids -> friendAdapter.pendingSentIds = ids }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            friendViewModel.incomingRequestMap.collect { map -> friendAdapter.incomingRequestMap = map }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            friendViewModel.actionState.collect { state ->
                when (state) {
                    is FriendActionState.RequestSent -> {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                        friendViewModel.resetActionState()
                    }
                    is FriendActionState.RequestAccepted -> {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                        friendViewModel.resetActionState()
                    }
                    is FriendActionState.Error -> {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        friendViewModel.resetActionState()
                    }
                    is FriendActionState.Success -> friendViewModel.resetActionState()
                    else -> Unit
                }
            }
        }

        chipExplore.setOnClickListener  { switchMode(FriendSheetMode.EXPLORE) }
        chipRequests.setOnClickListener { switchMode(FriendSheetMode.REQUESTS) }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                when (currentMode) {
                    FriendSheetMode.EXPLORE   -> friendViewModel.searchAllUsers(currentUserId, query)
                    FriendSheetMode.FRIENDS   -> friendViewModel.searchFriends(currentUserId, query)
                    FriendSheetMode.REQUESTS  -> applyRequestFilter(query)
                }
            }
        })

        // Abrir en el modo correcto
        switchMode(startMode)

        dialog.show()
    }

    // ── BottomSheet grupos ────────────────────────────────────────────────────

    private fun showGroupsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view   = layoutInflater.inflate(R.layout.bottomsheet_groups, null)
        dialog.setContentView(view)
        dialog.show()
    }
}
