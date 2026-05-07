package com.example.cosmos.ui.News

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
import com.example.cosmos.databinding.FragmentNewsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NewsFragment : Fragment() {

    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewsViewModel by viewModels()

    private lateinit var newsAdapter: NewsPostAdapter
    private var currentUserId = ""

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentUserId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        initUI()
        initListeners()
        observeViewModel()
        viewModel.loadNews(currentUserId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private fun initUI() {
        newsAdapter = NewsPostAdapter(
            currentUserId = currentUserId,
            memberNames = emptyMap(),
            onCrewClick = { post ->
                viewModel.loadCrewRates(post.eventId ?: "", post.memberIds)
            },
            onViewEvent = { eventId ->
                val bundle = Bundle().apply { putString("eventId", eventId) }
                findNavController().navigate(R.id.action_newsFragment_to_eventDetailFragment, bundle)
            }
        )
        binding.rvNews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = newsAdapter
        }
    }

    private fun initListeners() {
        binding.overlayRateDetail.overlayRoot.setOnClickListener {
            binding.overlayRateDetail.overlayRoot.isVisible = false
        }
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state is NewsUiState.Loading
                        binding.layoutEmpty.isVisible = state is NewsUiState.Empty
                        binding.rvNews.isVisible = state is NewsUiState.Success

                        if (state is NewsUiState.Success) {
                            // Update adapter with fresh member names
                            newsAdapter = NewsPostAdapter(
                                currentUserId = currentUserId,
                                memberNames = state.memberNames,
                                onCrewClick = { post ->
                                    viewModel.loadCrewRates(post.eventId ?: "", post.memberIds)
                                },
                                onViewEvent = { eventId ->
                                    val bundle = Bundle().apply { putString("eventId", eventId) }
                                    findNavController().navigate(
                                        R.id.action_newsFragment_to_eventDetailFragment, bundle
                                    )
                                }
                            )
                            binding.rvNews.adapter = newsAdapter
                            newsAdapter.submitList(state.posts)
                        }
                    }
                }
                launch {
                    viewModel.crewState.collect { state ->
                        if (state is CrewUiState.Ready) {
                            showCrewBottomSheet(state)
                            viewModel.resetCrewState()
                        }
                    }
                }
            }
        }
    }

    // ── Bottom sheet de tripulacion ──────────────────────────────────────────

    private fun showCrewBottomSheet(state: CrewUiState.Ready) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottomsheet_crew_rates, null)
        dialog.setContentView(sheetView)

        val rv = sheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvCrewRates)
        rv.layoutManager = LinearLayoutManager(requireContext())

        val crewItems = state.memberIds.map { memberId ->
            CrewRateItem(
                userId = memberId,
                username = state.memberNames[memberId] ?: "?",
                rate = state.rates.find { it.userId == memberId }
            )
        }

        rv.adapter = CrewRateAdapter(crewItems) { item ->
            dialog.dismiss()
            showRateOverlay(item)
        }

        dialog.show()
    }

    // ── Overlay de rate individual ──────────────────────────────────────────

    private fun showRateOverlay(item: CrewRateItem) {
        val overlay = binding.overlayRateDetail
        overlay.overlayRoot.isVisible = true

        overlay.tvOverlayUsername.text = "@${item.username}"

        if (item.rate != null) {
            val filled = item.rate.rating.toInt().coerceIn(0, 5)
            overlay.tvOverlayStars.text = "\u2605".repeat(filled) + "\u2606".repeat(5 - filled)
            overlay.tvOverlayStars.setTextColor(0xFFFFD700.toInt())
            overlay.tvOverlayRating.text = "%.1f / 5.0".format(item.rate.rating)
            overlay.tvOverlayRating.isVisible = true
            overlay.tvOverlayComment.text = item.rate.comment ?: ""
            overlay.tvOverlayComment.isVisible = !item.rate.comment.isNullOrBlank()
            overlay.tvOverlayNoRate.isVisible = false
        } else {
            overlay.tvOverlayStars.text = ""
            overlay.tvOverlayRating.isVisible = false
            overlay.tvOverlayComment.isVisible = false
            overlay.tvOverlayNoRate.isVisible = true
        }
    }
}
