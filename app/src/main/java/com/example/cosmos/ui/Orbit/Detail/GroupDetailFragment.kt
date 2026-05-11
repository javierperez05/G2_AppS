package com.example.cosmos.ui.Orbit.Detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentGroupDetailBinding
import com.example.cosmos.ui.Events.rvEvents.EventAdapter
import com.example.cosmos.ui.Orbit.GroupEventsUiState
import com.example.cosmos.ui.Orbit.GroupViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GroupDetailFragment : Fragment() {

    private var _binding: FragmentGroupDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GroupViewModel by activityViewModels()

    private val eventAdapter = EventAdapter { event ->
        val bundle = android.os.Bundle().apply { putString("eventId", event.id ?: "") }
        findNavController().navigate(R.id.action_groupDetailFragment_to_eventDetailFragment, bundle)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUI()
        initListeners()
        initData()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initUI() {
        binding.rvGroupEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
            itemAnimator = null
        }
    }

    private fun initListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
    }

    private fun initData() {
        val group = viewModel.selectedGroup.value ?: return
        with(binding) {
            tvGroupName.text        = group.name ?: "Órbita"
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
        viewModel.loadGroupEvents(group.eventIds)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.groupEventsState.collect { state ->
                    binding.progressBar.isVisible  = state is GroupEventsUiState.Loading
                    binding.layoutEmpty.isVisible  = state is GroupEventsUiState.Empty
                    binding.rvGroupEvents.isVisible = state is GroupEventsUiState.Success
                    if (state is GroupEventsUiState.Success) {
                        eventAdapter.submitList(state.events)
                    }
                }
            }
        }
    }
}
