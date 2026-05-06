package com.example.cosmos.ui.Profile

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
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    private val eventAdapter = ProfileEventAdapter { event ->
        val bundle = android.os.Bundle().apply { putString("eventId", event.id ?: "") }
        findNavController().navigate(R.id.action_profileFragment_to_eventDetailFragment, bundle)
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        initUI()
        initListeners()
        observeViewModel()
        viewModel.loadProfile(userId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private fun initUI() {
        binding.rvProfileEvents.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = eventAdapter
            itemAnimator = null
        }
    }

    private fun initListeners() {
        binding.btnProfileConfig.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_configFragment)
        }
        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_configFragment)
        }
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is ProfileUiState.Success) {
                        binding.tvProfileUsername.text = state.user.username ?: "Usuario"
                        binding.tvProfileBio.text = state.user.email ?: ""
                        binding.tvProfileEventCount.text = state.events.size.toString()
                        binding.tvProfileFriendCount.text = state.user.friends.size.toString()
                        binding.tvProfileOrbitCount.text = state.orbitCount.toString()
                        binding.layoutProfileEmpty.isVisible = state.events.isEmpty()
                        binding.rvProfileEvents.isVisible = state.events.isNotEmpty()
                        if (state.events.isNotEmpty()) eventAdapter.submitList(state.events)
                    }
                }
            }
        }
    }
}
