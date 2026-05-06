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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cosmos.databinding.FragmentNewsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NewsFragment : Fragment() {

    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewsViewModel by viewModels()

    private lateinit var newsAdapter: NewsEventAdapter

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        initUI(userId)
        observeViewModel()
        viewModel.loadNews(userId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private fun initUI(userId: String) {
        newsAdapter = NewsEventAdapter(
            userId = userId,
            onRateSubmit = { eventId, rate -> viewModel.submitRate(eventId, rate) }
        )
        binding.rvNews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = newsAdapter
        }
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.isVisible  = state is NewsUiState.Loading
                    binding.layoutEmpty.isVisible  = state is NewsUiState.Empty
                    binding.rvNews.isVisible       = state is NewsUiState.Success

                    if (state is NewsUiState.Success) {
                        newsAdapter.submitList(state.items)
                    }
                }
            }
        }
    }
}
