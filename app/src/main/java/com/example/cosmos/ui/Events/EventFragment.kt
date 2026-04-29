package com.example.cosmos.ui.Events

import android.os.Bundle
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
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentEventBinding
import com.example.cosmos.ui.Events.rvEvents.EventAdapter
import com.example.cosmos.ui.Events.rvEvents.EventUiState
import com.example.cosmos.ui.Events.rvEvents.EventViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EventFragment : Fragment() {

    private var _binding: FragmentEventBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EventViewModel by viewModels()

    private val eventAdapter = EventAdapter { event ->
        viewModel.selectEvent(event)
        findNavController().navigate(R.id.action_eventFragment_to_eventDetailFragment)
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUI()
        initListeners()
        observeViewModel()

        val userId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        viewModel.loadEvents(userId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private fun initUI() {
        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
        }
    }

    private fun initListeners() {
        binding.fabCreateEvent.setOnClickListener {
            findNavController().navigate(R.id.action_eventFragment_to_createEventFragment)
        }
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.isVisible = state is EventUiState.Loading
                    binding.layoutEmpty.isVisible  = state is EventUiState.Empty
                    binding.rvEvents.isVisible     = state is EventUiState.Success

                    if (state is EventUiState.Success) {
                        eventAdapter.submitList(state.events)
                    }
                }
            }
        }
    }
}