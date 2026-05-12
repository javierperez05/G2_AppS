package com.example.cosmos.ui.Events.rvEvents

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.EventType
import com.example.cosmos.Model.Firestore.Repositories.OrbitRepository
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.Group
import com.example.cosmos.Model.Users.User
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentCreateEventBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.example.cosmos.ui.Orbit.GroupViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class CreateEventFragment : Fragment() {

    private var _binding: FragmentCreateEventBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EventViewModel by viewModels()
    private val groupViewModel: GroupViewModel by androidx.fragment.app.activityViewModels()

    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var orbitRepository: OrbitRepository

    private var selectedDate: Date? = null
    private var selectedHour = 0
    private var selectedMinute = 0
    private var selectedDurationMinutes: Int? = null
    private var currentUserId = ""
    private val selectedMemberIds = mutableListOf<String>()
    private val selectedMembers = mutableListOf<User>()

    private var selectedImageUri: Uri? = null
    private var groupId: String? = null
    private var editEventId: String? = null

    // Orbitas: grupos seleccionados y miembros que vienen de grupos
    private val selectedGroupIds = mutableSetOf<String>()
    // Mapa: groupId -> lista de memberIds que vinieron de ese grupo
    private val groupMemberMap = mutableMapOf<String, List<String>>()
    private var userGroups: List<Group> = emptyList()
    private val orbitChipViews = mutableMapOf<String, android.widget.TextView>()

    private lateinit var searchAdapter: UserSearchAdapter
    private lateinit var membersAdapter: SelectedMembersAdapter

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            binding.btnAddEventImage.apply {
                setImageURI(uri)
                setPadding(0, 0, 0, 0)
                imageTintList = null
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initData()
        initUI()
        initListeners()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initData() {
        currentUserId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        groupId = arguments?.getString("groupId")
        editEventId = arguments?.getString("editEventId")

        if (editEventId != null) {
            // Edit mode: load event data into ViewModel (members will be loaded too)
            viewModel.loadEventForEdit(editEventId!!)
            binding.tvCreateEventTitle.text = "EDITAR MISIÓN"
            binding.btnCreateEvent.text = "Guardar cambios"
        } else if (currentUserId.isNotEmpty()) {
            selectedMemberIds.add(currentUserId)
        }

        // Cargar orbitas del usuario para poder invitar grupos enteros
        if (currentUserId.isNotEmpty()) {
            orbitRepository.getUserGroups(currentUserId) { groups ->
                userGroups = groups
                buildOrbitChips()
            }
        }
    }

    private fun initUI() {
        searchAdapter = UserSearchAdapter(emptyList()) { user ->
            addMemberToEvent(user)
        }
        binding.rvSearchSuggestions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        membersAdapter = SelectedMembersAdapter(selectedMembers) { user ->
            removeMemberFromEvent(user)
        }
        binding.rvMembersToInvite.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = membersAdapter
        }
    }

    private fun initListeners() {
        // Selector de imagen desde galeria
        binding.btnAddEventImage.setOnClickListener { pickImage.launch("image/*") }

        // Selector de fecha
        binding.btnSelectEventDate.setOnClickListener { showDatePicker() }

        // Selector de hora
        binding.btnSelectEventTime.setOnClickListener { showTimePicker() }

        // Buscador de miembros — busca en Firestore con cada letra
        binding.etMemberEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                buscarUsuarios(s.toString().trim())
            }
        })

        // Chips de duracion
        val durationChips = mapOf(
            binding.chipDuration30 to 30,
            binding.chipDuration60 to 60,
            binding.chipDuration120 to 120,
            binding.chipDuration180 to 180
        )
        durationChips.forEach { (chip, minutes) ->
            chip.setOnClickListener { selectDuration(minutes, durationChips.keys) }
        }

        // Crear evento
        binding.btnCreateEvent.setOnClickListener { saveEvent() }

        // Preview de ubicacion en el mapa
        binding.btnPreviewLocation.setOnClickListener {
            val query = binding.etEventLocation.text.toString().trim()
            if (query.isEmpty()) return@setOnClickListener
            openInMaps(query)
        }
    }

    private fun openInMaps(query: String) {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://maps.google.com/?q=${Uri.encode(query)}")))
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.createState.collect { state ->
                        when (state) {
                            is CreateEventUiState.Idle -> {
                                binding.btnCreateEvent.isEnabled = true
                                if (editEventId == null) binding.btnCreateEvent.text = "Launch Event"
                            }
                            is CreateEventUiState.Loading -> {
                                binding.btnCreateEvent.isEnabled = false
                                binding.btnCreateEvent.text = if (editEventId != null) "Guardando..." else "Launching..."
                            }
                            is CreateEventUiState.Success -> {
                                val gId = groupId
                                if (!gId.isNullOrEmpty() && state.eventId.isNotEmpty() && editEventId == null) {
                                    groupViewModel.linkEventToGroup(gId, state.eventId)
                                }
                                viewModel.resetCreateState()
                                findNavController().popBackStack()
                            }
                            is CreateEventUiState.Error -> {
                                binding.btnCreateEvent.isEnabled = true
                                binding.btnCreateEvent.text = if (editEventId != null) "Guardar cambios" else "Launch Event"
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetCreateState()
                            }
                        }
                    }
                }
                // Edit mode: prefill when event data arrives
                if (editEventId != null) {
                    launch {
                        viewModel.selectedEvent.collect { event ->
                            if (event != null) prefillEvent(event)
                        }
                    }
                    launch {
                        viewModel.selectedMembers.collect { members ->
                            if (members.isNotEmpty()) {
                                selectedMemberIds.clear()
                                selectedMembers.clear()
                                members.forEach { user ->
                                    user.id?.let { selectedMemberIds.add(it) }
                                    selectedMembers.add(user)
                                }
                                membersAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun prefillEvent(event: Event) {
        binding.etEventName.setText(event.title ?: "")
        binding.etEventDescription.setText(event.description ?: "")
        binding.etEventLocation.setText(event.location ?: "")

        if (event.date != null) {
            selectedDate = event.date
            val fmt = java.text.SimpleDateFormat("EEE, d MMM yyyy", java.util.Locale.getDefault())
            binding.tvDisplayDate.text = fmt.format(event.date)
            val cal = java.util.Calendar.getInstance().apply { time = event.date }
            selectedHour   = cal.get(java.util.Calendar.HOUR_OF_DAY)
            selectedMinute = cal.get(java.util.Calendar.MINUTE)
            binding.tvDisplayTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
        }

        val dur = event.durationMinutes
        if (dur != null && dur > 0) {
            selectedDurationMinutes = dur
            val label = if (dur < 60) "$dur min" else "${dur / 60}h" + if (dur % 60 > 0) " ${dur % 60}min" else ""
            binding.tvDurationLabel.text = label
            binding.tvDurationLabel.setTextColor(0xFFFFFFFF.toInt())
        }

        if (!event.imageURL.isNullOrEmpty()) {
            com.bumptech.glide.Glide.with(binding.btnAddEventImage)
                .load(event.imageURL).centerCrop()
                .into(binding.btnAddEventImage)
            binding.btnAddEventImage.setPadding(0, 0, 0, 0)
            binding.btnAddEventImage.imageTintList = null
            binding.btnAddEventImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
    }

    private fun buscarUsuarios(query: String) {
        if (query.isEmpty()) {
            searchAdapter.updateList(emptyList())
            binding.rvSearchSuggestions.isVisible = false
            return
        }
        userRepository.searchByUsername(query, currentUserId) { users ->
            val filtered = users.filter { !selectedMemberIds.contains(it.id) }
            searchAdapter.updateList(filtered)
            binding.rvSearchSuggestions.isVisible = filtered.isNotEmpty()
        }
    }

    private fun addMemberToEvent(user: User) {
        if (!selectedMemberIds.contains(user.id)) {
            user.id?.let { selectedMemberIds.add(it) }
            selectedMembers.add(user)
            membersAdapter.notifyItemInserted(selectedMembers.size - 1)
            binding.etMemberEmail.text.clear()
            binding.rvSearchSuggestions.isVisible = false
        }
    }

    private fun removeMemberFromEvent(user: User) {
        val index = selectedMembers.indexOf(user)
        if (index != -1) {
            selectedMemberIds.remove(user.id)
            selectedMembers.removeAt(index)
            membersAdapter.notifyItemRemoved(index)
        }
    }

    private fun selectDuration(minutes: Int, allChips: Set<android.view.View>) {
        selectedDurationMinutes = minutes
        allChips.forEach { chip ->
            (chip as android.widget.TextView).apply {
                setBackgroundResource(R.drawable.bg_chip_glass)
                setTextColor(0x88FFFFFF.toInt())
            }
        }
        (allChips.first { (it as android.widget.TextView).let { tv ->
            when (minutes) {
                30 -> tv.text == "30 min"
                60 -> tv.text == "1h"
                120 -> tv.text == "2h"
                180 -> tv.text == "3h"
                else -> false
            }
        } } as android.widget.TextView).apply {
            setBackgroundResource(R.drawable.bg_tab_selected)
            setTextColor(0xFFC4BCFF.toInt())
        }
        val label = if (minutes < 60) "${minutes} min"
                    else "${minutes / 60}h" + if (minutes % 60 > 0) " ${minutes % 60}min" else ""
        binding.tvDurationLabel.text = label
        binding.tvDurationLabel.setTextColor(0xFFFFFFFF.toInt())
    }

    // ── Orbitas ─────────────────────────────────────────────────────────────

    private fun buildOrbitChips() {
        if (_binding == null) return
        val ll = binding.llOrbitChips
        ll.removeAllViews()
        orbitChipViews.clear()

        if (userGroups.isEmpty()) return

        binding.tvOrbitLabel.isVisible = true
        binding.scrollOrbits.isVisible = true

        val density = resources.displayMetrics.density
        val chipH   = (36 * density).toInt()
        val chipPad = (14 * density).toInt()
        val chipMar = (8 * density).toInt()

        for (group in userGroups) {
            val gId = group.id ?: continue
            val chip = android.widget.TextView(requireContext()).apply {
                text = group.name ?: "Orbita"
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(chipPad, 0, chipPad, 0)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, chipH
                ).also { it.setMargins(0, 0, chipMar, 0) }
                textSize = 12f
                setBackgroundResource(R.drawable.bg_chip_glass)
                setTextColor(0x88FFFFFF.toInt())
                setOnClickListener { toggleGroup(gId, group) }
            }
            orbitChipViews[gId] = chip
            ll.addView(chip)
        }
    }

    private fun toggleGroup(groupId: String, group: Group) {
        if (selectedGroupIds.contains(groupId)) {
            // Deseleccionar grupo
            selectedGroupIds.remove(groupId)
            removeGroupMembers(groupId)
            orbitChipViews[groupId]?.apply {
                setBackgroundResource(R.drawable.bg_chip_glass)
                setTextColor(0x88FFFFFF.toInt())
            }
        } else {
            // Seleccionar grupo
            selectedGroupIds.add(groupId)
            addGroupMembers(groupId, group.memberIds)
            orbitChipViews[groupId]?.apply {
                setBackgroundResource(R.drawable.bg_tab_selected)
                setTextColor(0xFFC4BCFF.toInt())
            }
        }
    }

    private fun addGroupMembers(groupId: String, memberIds: List<String>) {
        // IDs nuevos que no estan ya seleccionados
        val newIds = memberIds.filter { !selectedMemberIds.contains(it) }
        groupMemberMap[groupId] = newIds
        selectedMemberIds.addAll(newIds)

        if (newIds.isEmpty()) return

        // Fetch User objects para mostrar en la lista
        userRepository.getUsersByIds(newIds) { users ->
            if (_binding == null) return@getUsersByIds
            selectedMembers.addAll(users)
            membersAdapter.notifyDataSetChanged()
        }
    }

    private fun removeGroupMembers(groupId: String) {
        val idsFromGroup = groupMemberMap.remove(groupId) ?: return

        // Solo quitar los IDs que no estan en otro grupo seleccionado ni fueron
        // anadidos individualmente (los que siguen en algun otro grupo los dejamos)
        val idsInOtherGroups = groupMemberMap.values.flatten().toSet()
        val idsToRemove = idsFromGroup.filter { it !in idsInOtherGroups }

        selectedMemberIds.removeAll(idsToRemove.toSet())
        selectedMembers.removeAll { it.id in idsToRemove }
        membersAdapter.notifyDataSetChanged()
    }

    // ── Date / Time pickers ─────────────────────────────────────────────────

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker().build()
        picker.addOnPositiveButtonClickListener { selection ->
            selectedDate = Date(selection)
            binding.tvDisplayDate.text = picker.headerText
        }
        picker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                binding.tvDisplayTime.text = String.format("%02d:%02d", hour, minute)
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun saveEvent() {
        val title = binding.etEventName.text.toString().trim()
        if (title.isEmpty()) {
            binding.etEventName.error = "Mission name is required"
            return
        }
        if (selectedDate == null) {
            Snackbar.make(binding.root, "Select a date", Snackbar.LENGTH_SHORT).show()
            return
        }

        // Combina fecha + hora seleccionadas
        val cal = Calendar.getInstance().apply {
            time = selectedDate!!
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
        }

        val editId = editEventId
        if (editId != null) {
            // Edit mode: preserve existing event fields (adminIds, type, finished, etc.)
            val existing = viewModel.selectedEvent.value ?: return
            val updatedEvent = existing.copy(
                title           = title,
                description     = binding.etEventDescription.text.toString().trim(),
                location        = binding.etEventLocation.text.toString().trim().ifBlank { null },
                date            = cal.time,
                durationMinutes = selectedDurationMinutes,
                memberIds       = selectedMemberIds.distinct()
            )
            viewModel.updateEvent(updatedEvent, selectedImageUri)
        } else {
            val newEvent = Event(
                title           = title,
                description     = binding.etEventDescription.text.toString().trim(),
                location        = binding.etEventLocation.text.toString().trim().ifBlank { null },
                date            = cal.time,
                durationMinutes = selectedDurationMinutes,
                adminIds        = listOf(currentUserId),
                memberIds       = selectedMemberIds.distinct(),
                type            = EventType.DEFAULT
            )
            viewModel.createEvent(newEvent, selectedImageUri)
        }
    }
}