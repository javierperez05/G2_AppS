package com.example.cosmos.ui.Events.rvEvents

import android.app.TimePickerDialog
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
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.User
import com.example.cosmos.databinding.FragmentCreateEventBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
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

    @Inject
    lateinit var userRepository: UserRepository

    private var selectedDate: Date? = null
    private var selectedHour = 0
    private var selectedMinute = 0
    private var selectedDurationMinutes: Int? = null
    private var currentUserId = ""
    private val selectedMemberIds = mutableListOf<String>()
    private val selectedMembers = mutableListOf<User>()

    private var selectedImageUri: Uri? = null

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
        if (currentUserId.isNotEmpty()) {
            selectedMemberIds.add(currentUserId)
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
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.createState.collect { state ->
                    when (state) {
                        is CreateEventUiState.Idle -> {
                            binding.btnCreateEvent.isEnabled = true
                            binding.btnCreateEvent.text = "Launch Event"
                        }
                        is CreateEventUiState.Loading -> {
                            binding.btnCreateEvent.isEnabled = false
                            binding.btnCreateEvent.text = "Launching..."
                        }
                        is CreateEventUiState.Success -> {
                            viewModel.resetCreateState()
                            findNavController().popBackStack()
                        }
                        is CreateEventUiState.Error -> {
                            binding.btnCreateEvent.isEnabled = true
                            binding.btnCreateEvent.text = "Launch Event"
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            viewModel.resetCreateState()
                        }
                    }
                }
            }
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

        val newEvent = Event(
            title = title,
            description = binding.etEventDescription.text.toString().trim(),
            location = binding.etEventLocation.text.toString().trim().ifBlank { null },
            date = cal.time,
            durationMinutes = selectedDurationMinutes,
            adminIds = listOf(currentUserId),
            memberIds = selectedMemberIds.distinct(),
            type = EventType.DEFAULT
        )
        viewModel.createEvent(newEvent)
    }
}