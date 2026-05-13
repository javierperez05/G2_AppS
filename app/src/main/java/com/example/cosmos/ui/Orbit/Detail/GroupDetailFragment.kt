package com.example.cosmos.ui.Orbit.Detail

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.User
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentGroupDetailBinding
import com.example.cosmos.ui.Events.Home.EventAdapter
import com.example.cosmos.ui.Events.Create.UserSearchAdapter
import com.example.cosmos.ui.Orbit.GroupDetailActionState
import com.example.cosmos.ui.Orbit.GroupEventsUiState
import com.example.cosmos.ui.Orbit.GroupMembersUiState
import com.example.cosmos.ui.Orbit.GroupViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GroupDetailFragment : Fragment() {

    private var _binding: FragmentGroupDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GroupViewModel by activityViewModels()

    @Inject
    lateinit var userRepository: UserRepository

    private var currentUserId = ""
    private var currentGroupId = ""
    private var isAdmin = false
    private var isMember = false
    private var isInvited = false

    private var currentTab = Tab.MISSIONS
    private enum class Tab { MISSIONS, MEMBERS }

    private val eventAdapter = EventAdapter { event ->
        val bundle = Bundle().apply { putString("eventId", event.id ?: "") }
        findNavController().navigate(R.id.action_groupDetailFragment_to_eventDetailFragment, bundle)
    }

    private lateinit var memberAdapter: GroupMemberAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentUserId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        initData()
        initUI()
        initListeners()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private fun initData() {
        val group = viewModel.selectedGroup.value ?: return
        currentGroupId = group.id ?: ""
        isAdmin = group.adminIds.contains(currentUserId)
        isMember = group.memberIds.contains(currentUserId)
        isInvited = group.invitedIds.contains(currentUserId)

        with(binding) {
            tvGroupName.text = group.name ?: "Órbita"
            tvGroupDescription.text = group.description ?: ""
            tvGroupDescription.isVisible = !group.description.isNullOrBlank()
            tvGroupMeta.text = "${group.memberIds.size} miembro${if (group.memberIds.size == 1) "" else "s"}"
            if (!group.imageUrl.isNullOrBlank()) {
                Glide.with(this@GroupDetailFragment)
                    .load(group.imageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_circle_profile)
                    .into(ivGroupImage)
            }
        }

        // Invite banner solo para usuarios invitados (aún no miembros)
        binding.cardInviteBanner.isVisible = isInvited && !isMember

        // Action buttons visibility
        binding.btnCreateEvent.isVisible = isMember || isAdmin
        binding.btnInvite.isVisible = isAdmin
        binding.btnLeaveGroup.isVisible = isMember && !isAdmin

        // Load initial tab
        viewModel.loadGroupEvents(group.eventIds)
    }

    private fun initUI() {
        binding.rvGroupEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
            itemAnimator = null
        }

        memberAdapter = GroupMemberAdapter(
            currentUserId = currentUserId,
            isAdmin = isAdmin,
            onKick = { user ->
                confirmKick(user)
            }
        )
        binding.rvGroupMembers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = memberAdapter
            itemAnimator = null
        }

        setTab(Tab.MISSIONS)
    }

    private fun initListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.chipMissions.setOnClickListener {
            if (currentTab != Tab.MISSIONS) {
                setTab(Tab.MISSIONS)
                val group = viewModel.selectedGroup.value ?: return@setOnClickListener
                viewModel.loadGroupEvents(group.eventIds)
            }
        }

        binding.chipMembers.setOnClickListener {
            if (currentTab != Tab.MEMBERS) {
                setTab(Tab.MEMBERS)
                val group = viewModel.selectedGroup.value ?: return@setOnClickListener
                viewModel.loadGroupMembers(group.memberIds, group.invitedIds)
            }
        }

        binding.btnCreateEvent.setOnClickListener {
            val bundle = Bundle().apply { putString("groupId", currentGroupId) }
            findNavController().navigate(R.id.action_groupDetailFragment_to_createEventFragment, bundle)
        }

        binding.btnInvite.setOnClickListener { showInviteSheet() }

        binding.btnLeaveGroup.setOnClickListener {
            showConfirmDialog(
                title = "Salir de la orbita",
                message = "¿Seguro que quieres salir de ${viewModel.selectedGroup.value?.name}?",
                confirmText = "Salir"
            ) { viewModel.leaveGroup(currentGroupId, currentUserId) }
        }

        binding.btnAcceptInvite.setOnClickListener {
            viewModel.acceptGroupInvite(currentGroupId, currentUserId)
        }

        binding.btnRejectInvite.setOnClickListener {
            showConfirmDialog(
                title = "Rechazar invitacion",
                message = "¿Rechazar la invitacion a esta orbita?",
                confirmText = "Rechazar"
            ) { viewModel.rejectGroupInvite(currentGroupId, currentUserId) }
        }
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.groupEventsState.collect { state ->
                        if (currentTab != Tab.MISSIONS) return@collect
                        binding.progressBar.isVisible = state is GroupEventsUiState.Loading
                        binding.layoutEmpty.isVisible = state is GroupEventsUiState.Empty
                        binding.rvGroupEvents.isVisible = state is GroupEventsUiState.Success
                        if (state is GroupEventsUiState.Success) {
                            eventAdapter.submitList(state.events)
                        }
                    }
                }
                launch {
                    viewModel.groupMembersState.collect { state ->
                        if (currentTab != Tab.MEMBERS) return@collect
                        binding.progressBar.isVisible = state is GroupMembersUiState.Loading
                        if (state is GroupMembersUiState.Success) {
                            binding.progressBar.isVisible = false
                            val group = viewModel.selectedGroup.value
                            val items = buildMemberItems(state.members, state.invitedUsers, group?.adminIds ?: emptyList())
                            memberAdapter.submitList(items)
                            binding.rvGroupMembers.isVisible = items.isNotEmpty()
                            binding.layoutEmpty.isVisible = items.isEmpty()
                        }
                    }
                }
                launch {
                    viewModel.detailActionState.collect { state ->
                        when (state) {
                            is GroupDetailActionState.Success -> {
                                if (state.message.isNotEmpty()) {
                                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                                }
                                viewModel.resetDetailActionState()
                                // Si salió o rechazó, volver atrás
                                val msg = state.message
                                if (msg.contains("salido") || msg.contains("rechazar", ignoreCase = true) || msg.contains("expulsado", ignoreCase = true)) {
                                    findNavController().navigateUp()
                                } else if (msg.contains("unido")) {
                                    // Actualizar UI: ahora es miembro
                                    binding.cardInviteBanner.isVisible = false
                                    binding.btnCreateEvent.isVisible = true
                                    binding.btnLeaveGroup.isVisible = true
                                }
                            }
                            is GroupDetailActionState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetDetailActionState()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setTab(tab: Tab) {
        currentTab = tab
        val selectedBg = R.drawable.bg_tab_selected
        val defaultBg  = android.R.color.transparent

        if (tab == Tab.MISSIONS) {
            binding.chipMissions.setBackgroundResource(selectedBg)
            binding.chipMissions.setTextColor(0xFFFFFFFF.toInt())
            binding.chipMembers.setBackgroundResource(defaultBg)
            binding.chipMembers.setTextColor(0x66FFFFFF.toInt())
            binding.rvGroupEvents.isVisible = false
            binding.rvGroupMembers.isVisible = false
        } else {
            binding.chipMembers.setBackgroundResource(selectedBg)
            binding.chipMembers.setTextColor(0xFFFFFFFF.toInt())
            binding.chipMissions.setBackgroundResource(defaultBg)
            binding.chipMissions.setTextColor(0x66FFFFFF.toInt())
            binding.rvGroupEvents.isVisible = false
            binding.rvGroupMembers.isVisible = false
        }
        binding.progressBar.isVisible = true
        binding.layoutEmpty.isVisible = false
    }

    private fun buildMemberItems(
        members: List<User>,
        invited: List<User>,
        adminIds: List<String>
    ): List<GroupMemberItem> {
        val memberItems = members.map { user ->
            GroupMemberItem(
                user = user,
                isAdmin = adminIds.contains(user.id),
                isInvited = false,
                isSelf = user.id == currentUserId
            )
        }
        val invitedItems = invited.map { user ->
            GroupMemberItem(
                user = user,
                isAdmin = false,
                isInvited = true,
                isSelf = user.id == currentUserId
            )
        }
        return memberItems + invitedItems
    }

    private fun confirmKick(user: User) {
        showConfirmDialog(
            title = "Expulsar de la orbita",
            message = "¿Expulsar a @${user.username}?",
            confirmText = "Expulsar"
        ) { viewModel.kickMember(currentGroupId, user.id ?: return@showConfirmDialog) }
    }

    // ── Bottom sheet para invitar ─────────────────────────────────────────────

    private fun showInviteSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottomsheet_friends, null)
        dialog.setContentView(sheetView)

        val etSearch = sheetView.findViewById<EditText>(R.id.etSearchFriend)
        val tvLabel  = sheetView.findViewById<android.widget.TextView>(R.id.tvFriendsLabel)
        val tvEmpty  = sheetView.findViewById<android.widget.TextView>(R.id.tvFriendsEmpty)
        val rv       = sheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvFriends)

        // Ocultar chips de modo (no necesarios aquí)
        sheetView.findViewById<android.widget.TextView>(R.id.chipExplore).isVisible = false
        sheetView.findViewById<android.widget.TextView>(R.id.chipRequests).isVisible = false

        tvLabel.text = "INVITAR A LA ÓRBITA"
        etSearch.hint = "Buscar usuarios..."

        val group = viewModel.selectedGroup.value
        val existingMemberAndInvitedIds = ((group?.memberIds ?: emptyList()) + (group?.invitedIds ?: emptyList())).toSet()

        val searchAdapter = UserSearchAdapter(emptyList()) { user ->
            viewModel.inviteToGroup(currentGroupId, user.id ?: return@UserSearchAdapter)
            Snackbar.make(binding.root, "Invitación enviada a @${user.username}", Snackbar.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        rv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    searchAdapter.updateList(emptyList())
                    tvEmpty.isVisible = false
                    rv.isVisible = false
                    return
                }
                userRepository.searchByUsername(query, currentUserId) { users ->
                    val filtered = users.filter { !existingMemberAndInvitedIds.contains(it.id) }
                    searchAdapter.updateList(filtered)
                    rv.isVisible = filtered.isNotEmpty()
                    tvEmpty.isVisible = filtered.isEmpty()
                    if (filtered.isEmpty()) tvEmpty.text = "Sin resultados"
                }
            }
        })

        dialog.show()
    }

    // ── Dialog de confirmacion custom ────────────────────────────────────────

    private fun showConfirmDialog(
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_confirm, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.findViewById<TextView>(R.id.tvDialogTitle).text = title
        view.findViewById<TextView>(R.id.tvDialogMessage).text = message
        view.findViewById<TextView>(R.id.btnDialogConfirm).text = confirmText
        view.findViewById<TextView>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnDialogConfirm).setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
    }
}
