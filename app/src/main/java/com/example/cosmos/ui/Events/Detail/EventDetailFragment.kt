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
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.EventType
import com.example.cosmos.databinding.FragmentEventDetailBinding
import com.example.cosmos.ui.Events.Detail.EventDetailUiState
import com.example.cosmos.ui.Events.Detail.EventDetailViewModel
import com.example.cosmos.ui.Events.Detail.ForumThreadAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class EventDetailFragment : Fragment() {

    private var _binding: FragmentEventDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EventDetailViewModel by viewModels()

    private val threadAdapter = ForumThreadAdapter { thread ->
        replyingToThreadId = thread.id
        binding.layoutReplyContext.isVisible = true
        binding.tvReplyContext.text = "\u21A9  ${thread.authorName}: ${thread.text}"
        binding.etInput.hint = "Responder..."
        binding.etInput.requestFocus()
    }

    private var eventId: String = ""
    private var userId: String = ""
    private var replyingToThreadId: String? = null

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        eventId = arguments?.getString("eventId") ?: ""
        userId = activity?.intent?.getStringExtra("USER_ID") ?: ""

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
        if (eventId.isNotEmpty()) viewModel.loadEvent(eventId)
    }

    private fun initUI() {
        binding.rvThreads.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = threadAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun initListeners() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.btnCancelReply.setOnClickListener {
            replyingToThreadId = null
            binding.layoutReplyContext.isVisible = false
            binding.etInput.hint = "Transmite algo..."
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text?.toString() ?: return@setOnClickListener
            if (text.isBlank()) return@setOnClickListener

            val threadId = replyingToThreadId
            if (threadId != null) {
                viewModel.postReply(eventId, threadId, userId, text)
                replyingToThreadId = null
                binding.layoutReplyContext.isVisible = false
                binding.etInput.hint = "Transmite algo..."
            } else {
                viewModel.postQuestion(eventId, userId, text)
            }
            binding.etInput.setText("")
        }
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressGlobal.isVisible = state is EventDetailUiState.Loading
                    binding.scrollContent.isVisible = state is EventDetailUiState.Success ||
                            state is EventDetailUiState.Error

                    when (state) {
                        is EventDetailUiState.Success -> {
                            bindEvent(state.event, state.memberNames)
                            val threads = state.threads
                            binding.progressThreads.isVisible = false
                            binding.tvEmptyThreads.isVisible = threads.isEmpty()
                            binding.rvThreads.isVisible = threads.isNotEmpty()
                            if (threads.isNotEmpty()) threadAdapter.submitList(threads)
                            binding.tvThreadCount.text = "${threads.size} transmisiones"
                        }
                        is EventDetailUiState.Error -> {
                            binding.tvEmptyThreads.isVisible = true
                            binding.tvEmptyThreads.text = state.message
                            binding.rvThreads.isVisible = false
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun bindEvent(event: Event, memberNames: Map<String, String>) {
        val fmt = SimpleDateFormat("EEE, d MMM yyyy \u00B7 HH:mm", Locale.getDefault())

        binding.tvToolbarTitle.text = event.title ?: "Evento"
        binding.tvEventEmoji.text = if (event.type == EventType.SECRET) "\uD83D\uDD2E" else "\uD83D\uDE80"
        binding.tvEventTitle.text = event.title ?: ""
        binding.tvEventDate.text = event.date?.let { fmt.format(it) } ?: "Sin fecha"
        binding.tvEventLocation.text = event.location?.ifBlank { "Sin ubicacion" } ?: "Sin ubicacion"
        binding.tvEventDescription.text = event.description?.ifBlank { "" } ?: ""
        binding.tvEventDescription.isVisible = !event.description.isNullOrBlank()
        binding.tvMemberCount.text = "${event.memberIds.size} astronautas"
        binding.tvMemberNames.text = memberNames.values.joinToString("  \u00B7  ")
    }
}
