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
            }
        }
    }

    // ── BottomSheet amigos ────────────────────────────────────────────────────

    private enum class FriendSheetMode { FRIENDS, EXPLORE, REQUESTS }

    private fun showFriendsBottomSheet() {
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
            // Reset all chips
            chipExplore.setBackgroundResource(R.drawable.bg_chip_glass)
            chipExplore.setTextColor(0x88FFFFFF.toInt())
            chipRequests.setBackgroundResource(R.drawable.bg_chip_glass)
            chipRequests.setTextColor(0x88FFFFFF.toInt())
            // Highlight active
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

        fun switchMode(mode: FriendSheetMode) {
            if (currentMode == mode && mode != FriendSheetMode.FRIENDS) {
                // Toggle off → back to friends
                currentMode = FriendSheetMode.FRIENDS
            } else {
                currentMode = mode
            }
            friendAdapter.isRequestMode = currentMode == FriendSheetMode.REQUESTS
            updateChipStyles()

            when (currentMode) {
                FriendSheetMode.FRIENDS -> {
                    tvLabel.text = "TUS AMIGOS"
                    etSearch.isVisible = true
                    friendViewModel.loadFriends(currentUserId)
                }
                FriendSheetMode.EXPLORE -> {
                    tvLabel.text = "EXPLORANDO EL COSMOS"
                    etSearch.isVisible = true
                    val query = etSearch.text.toString().trim()
                    if (query.isNotBlank()) friendViewModel.searchAllUsers(currentUserId, query)
                    else friendViewModel.searchAllUsers(currentUserId, "")
                }
                FriendSheetMode.REQUESTS -> {
                    tvLabel.text = "SOLICITUDES PENDIENTES"
                    etSearch.isVisible = false
                    friendViewModel.loadIncomingRequests(currentUserId)
                }
            }
        }

        // Observe friend list state
        viewLifecycleOwner.lifecycleScope.launch {
            friendViewModel.friendsState.collect { state ->
                when (state) {
                    is FriendUiState.Success -> {
                        tvEmpty.isVisible = false
                        rvFriends.isVisible = true
                        friendAdapter.submitList(state.users)
                    }
                    is FriendUiState.Empty -> {
                        tvEmpty.isVisible = true
                        rvFriends.isVisible = false
                        tvEmpty.text = when (currentMode) {
                            FriendSheetMode.REQUESTS -> "Sin solicitudes pendientes"
                            FriendSheetMode.EXPLORE -> "Sin resultados"
                            else -> "Sin amigos"
                        }
                    }
                    else -> Unit
                }
            }
        }

        // Observe friendIds for button state
        viewLifecycleOwner.lifecycleScope.launch {
            friendViewModel.friendIds.collect { ids ->
                friendAdapter.friendIds = ids
            }
        }

        // Observe pending sent IDs
        viewLifecycleOwner.lifecycleScope.launch {
            friendViewModel.pendingSentIds.collect { ids ->
                friendAdapter.pendingSentIds = ids
            }
        }

        // Observe incoming request map
        viewLifecycleOwner.lifecycleScope.launch {
            friendViewModel.incomingRequestMap.collect { map ->
                friendAdapter.incomingRequestMap = map
            }
        }

        // Observe action state for snackbar feedback
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

        // Chip listeners
        chipExplore.setOnClickListener { switchMode(FriendSheetMode.EXPLORE) }
        chipRequests.setOnClickListener { switchMode(FriendSheetMode.REQUESTS) }

        // Search listener
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                when (currentMode) {
                    FriendSheetMode.EXPLORE -> friendViewModel.searchAllUsers(currentUserId, query)
                    FriendSheetMode.FRIENDS -> friendViewModel.searchFriends(currentUserId, query)
                    else -> {}
                }
            }
        })

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
