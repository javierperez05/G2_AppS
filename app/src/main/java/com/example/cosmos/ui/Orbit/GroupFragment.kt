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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        binding.fabNewGroup.setOnClickListener { showCreateGroupDialog() }
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

    private fun showFriendsBottomSheet() {
        val dialog    = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottomsheet_friends, null)
        dialog.setContentView(sheetView)

        val etSearch     = sheetView.findViewById<EditText>(R.id.etSearchFriend)
        val chipExplore  = sheetView.findViewById<TextView>(R.id.chipExplore)
        val tvLabel      = sheetView.findViewById<TextView>(R.id.tvFriendsLabel)
        val tvEmpty      = sheetView.findViewById<TextView>(R.id.tvFriendsEmpty)
        val rvFriends    = sheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvFriends)

        var exploreActive = false

        val friendAdapter = FriendAdapter { user, isFriend ->
            if (isFriend) {
                friendViewModel.removeFriend(currentUserId, user.id ?: return@FriendAdapter)
            } else {
                friendViewModel.addFriend(currentUserId, user.id ?: return@FriendAdapter)
            }
        }

        rvFriends.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = friendAdapter
        }

        // Observar estado de amigos
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
                    }
                    else -> Unit
                }
            }
        }

        // Observar friendIds para actualizar los botones
        viewLifecycleOwner.lifecycleScope.launch {
            friendViewModel.friendIds.collect { ids ->
                friendAdapter.friendIds = ids
            }
        }

        // Chip exploración
        chipExplore.setOnClickListener {
            exploreActive = !exploreActive
            if (exploreActive) {
                chipExplore.setBackgroundResource(R.drawable.bg_tab_selected)
                chipExplore.setTextColor(0xFFC4BCFF.toInt())
                tvLabel.text = "EXPLORANDO EL COSMOS"
                friendViewModel.searchAllUsers(currentUserId, etSearch.text.toString())
            } else {
                chipExplore.setBackgroundResource(R.drawable.bg_chip_glass)
                chipExplore.setTextColor(0x88FFFFFF.toInt())
                tvLabel.text = "TUS AMIGOS"
                friendViewModel.loadFriends(currentUserId)
            }
        }

        // Buscador
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (exploreActive) friendViewModel.searchAllUsers(currentUserId, query)
                else friendViewModel.searchFriends(currentUserId, query)
            }
        })

        dialog.show()
    }

    // ── BottomSheet grupos ────────────────────────────────────────────────────

    private fun showGroupsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view   = layoutInflater.inflate(R.layout.bottomsheet_groups, null)
        dialog.setContentView(view)
        // TODO: RV de grupos ordenados por actividad
        dialog.show()
    }

    // ── Dialog crear grupo ────────────────────────────────────────────────────

    private fun showCreateGroupDialog() {
        val nameInput = EditText(requireContext()).apply { hint = "Nombre de la órbita" }
        val descInput = EditText(requireContext()).apply { hint = "Descripción (opcional)" }
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(nameInput)
            addView(descInput)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nueva órbita")
            .setView(container)
            .setPositiveButton("Crear") { _, _ ->
                groupViewModel.createGroup(
                    name        = nameInput.text.toString(),
                    description = descInput.text.toString(),
                    userId      = currentUserId
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
