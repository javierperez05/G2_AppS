package com.example.cosmos.ui.Events.rvEvents

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class CreateEventFragment : Fragment() {

    private var _binding: FragmentCreateEventBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EventViewModel by viewModels()

    @Inject
    lateinit var userRepository: UserRepository

    private var allFriendsList = listOf<User>()
    private var selectedDate: Date? = null
    private var currentUserId: String = ""
    private val selectedMemberIds = mutableListOf<String>()

    private lateinit var searchAdapter: UserSearchAdapter

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
            userRepository.getFriends(currentUserId) { friends ->
                allFriendsList = friends
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
    }

    private fun initListeners() {
        binding.btnSelectEventDate.setOnClickListener { showDatePicker() }
        binding.btnCreateEvent.setOnClickListener { saveEvent() }
        binding.etMemberSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarAmigos(s.toString().trim())
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.createState.collect { state ->
                    when (state) {
                        is CreateEventUiState.Idle -> {
                            binding.btnCreateEvent.isEnabled = true
                            binding.btnCreateEvent.text = "LAUNCH EVENT"
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
                            binding.btnCreateEvent.text = "LAUNCH EVENT"
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            viewModel.resetCreateState()
                        }
                    }
                }
            }
        }
    }

    private fun filtrarAmigos(query: String) {
        if (query.isEmpty()) {
            binding.rvSearchSuggestions.visibility = View.GONE
            return
        }
        val filtered = allFriendsList.filter {
            it.username?.contains(query, ignoreCase = true) == true
        }
        searchAdapter.updateList(filtered)
        binding.rvSearchSuggestions.isVisible = filtered.isNotEmpty()
    }

    private fun addMemberToEvent(user: User) {
        if (!selectedMemberIds.contains(user.id)) {
            user.id?.let { selectedMemberIds.add(it) }
            binding.etMemberSearch.text.clear()
            binding.rvSearchSuggestions.visibility = View.GONE
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker().build()
        picker.addOnPositiveButtonClickListener { selection ->
            selectedDate = Date(selection)
            binding.tvDisplayDate.text = picker.headerText
        }
        picker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun saveEvent() {
        val title = binding.etEventName.text.toString().trim()
        if (title.isEmpty()) {
            binding.etEventName.error = "Mission name is required"
            return
        }
        if (selectedDate == null) {
            Snackbar.make(binding.root, "Select a date for the mission", Snackbar.LENGTH_SHORT).show()
            return
        }
        val newEvent = Event(
            title     = title,
            date      = selectedDate,
            adminIds  = listOf(currentUserId),
            memberIds = selectedMemberIds.distinct(),
            type      = EventType.DEFAULT
        )
        viewModel.createEvent(newEvent)
    }
}