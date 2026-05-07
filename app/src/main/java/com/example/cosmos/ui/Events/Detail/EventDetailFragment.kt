package com.example.cosmos.ui.Events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RatingBar
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
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentEventDetailBinding
import com.example.cosmos.ui.Events.Detail.EventDetailUiState
import com.example.cosmos.ui.Events.Detail.EventDetailViewModel
import com.example.cosmos.ui.Events.Detail.ForumThreadAdapter
import com.example.cosmos.ui.Events.Detail.PostUiState
import com.example.cosmos.ui.Events.Detail.RateUiState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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
        if (eventId.isNotEmpty()) viewModel.loadEvent(eventId, userId)
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

        binding.btnFinishEvent.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Finalizar mision")
                .setMessage("Esto marcara el evento como completado para todos los miembros.")
                .setPositiveButton("Finalizar") { _, _ ->
                    viewModel.finishEvent(eventId)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        binding.btnRate.setOnClickListener { showRateDialog() }

        binding.btnPublishPost.setOnClickListener {
            viewModel.publishPost(eventId, userId)
        }
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressGlobal.isVisible = state is EventDetailUiState.Loading
                        binding.scrollContent.isVisible = state is EventDetailUiState.Success ||
                                state is EventDetailUiState.Error

                        when (state) {
                            is EventDetailUiState.Success -> {
                                bindEvent(state.event, state.memberNames)
                                bindFinishState(state.event, state.canFinish, state.hasRated, state.hasPosted)

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
                launch {
                    viewModel.rateState.collect { state ->
                        when (state) {
                            is RateUiState.Success -> {
                                Snackbar.make(binding.root, "Valoracion enviada", Snackbar.LENGTH_SHORT).show()
                                viewModel.resetRateState()
                            }
                            is RateUiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetRateState()
                            }
                            else -> {}
                        }
                    }
                }
                launch {
                    viewModel.postState.collect { state ->
                        when (state) {
                            is PostUiState.Success -> {
                                Snackbar.make(binding.root, "Publicado en News", Snackbar.LENGTH_SHORT).show()
                                viewModel.resetPostState()
                            }
                            is PostUiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetPostState()
                            }
                            else -> {}
                        }
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

        // Duracion
        val dur = event.durationMinutes
        if (dur != null && dur > 0) {
            binding.tvDuration.isVisible = true
            val label = if (dur < 60) "$dur min"
                        else "${dur / 60}h" + if (dur % 60 > 0) " ${dur % 60}min" else ""
            binding.tvDuration.text = "Duracion aproximada: $label"
        } else {
            binding.tvDuration.isVisible = false
        }
    }

    private fun bindFinishState(event: Event, canFinish: Boolean, hasRated: Boolean, hasPosted: Boolean = false) {
        val isAdmin = event.adminIds.contains(userId)

        // Boton finalizar: solo admin + tiempo pasado + no finalizado
        binding.btnFinishEvent.isVisible = isAdmin && canFinish && !event.finished

        // Card mision completada
        binding.cardFinished.isVisible = event.finished
        if (event.finished) {
            binding.btnRate.isVisible = !hasRated
            binding.tvAlreadyRated.isVisible = hasRated
            // Publicar: solo si ya ha valorado y no ha posteado
            binding.btnPublishPost.isVisible = hasRated && !hasPosted
            binding.tvAlreadyPosted.isVisible = hasPosted
        }
    }

    // ── Dialog de valoracion ────────────────────────────────────────────────

    private fun showRateDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 16)
        }

        val ratingBar = RatingBar(requireContext(), null, android.R.attr.ratingBarStyle).apply {
            numStars = 5
            stepSize = 0.5f
            rating = 3f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
        }

        val etComment = EditText(requireContext()).apply {
            hint = "Comentario (opcional)"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x55FFFFFF)
            setPadding(0, 32, 0, 0)
        }

        container.addView(ratingBar)
        container.addView(etComment)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Valorar mision")
            .setView(container)
            .setPositiveButton("Enviar") { _, _ ->
                viewModel.submitRate(
                    eventId = eventId,
                    userId = userId,
                    rating = ratingBar.rating,
                    comment = etComment.text.toString().trim()
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
