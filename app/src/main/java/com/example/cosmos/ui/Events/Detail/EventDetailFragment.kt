package com.example.cosmos.ui.Events

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.EventItem
import com.example.cosmos.Model.Event.EventType
import com.example.cosmos.Model.Users.User
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentEventDetailBinding
import com.example.cosmos.ui.Events.Detail.EventDetailUiState
import com.example.cosmos.ui.Events.Detail.EventDetailViewModel
import com.example.cosmos.ui.Events.Detail.EventItemAdapter
import com.example.cosmos.ui.Events.Detail.EventMemberAdapter
import com.example.cosmos.ui.Events.Detail.ForumThreadAdapter
import com.example.cosmos.ui.Events.Detail.PostUiState
import com.example.cosmos.ui.Events.Detail.RateUiState
import com.example.cosmos.ui.Events.Detail.Settlement
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

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

    private lateinit var memberAdapter: EventMemberAdapter
    private lateinit var itemAdapter: EventItemAdapter
    private var currentMembers: List<User> = emptyList()

    private var eventId: String = ""
    private var userId: String = ""
    private var replyingToThreadId: String? = null
    private var pendingPostImageUris: List<Uri> = emptyList()

    private val pickPostImages = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        pendingPostImageUris = uris
        viewModel.publishPost(eventId, userId, pendingPostImageUris)
    }

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
        memberAdapter = EventMemberAdapter()
        binding.rvEventMembers.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = memberAdapter
        }

        itemAdapter = EventItemAdapter(
            currentUserId = userId,
            memberNames   = emptyMap(),
            onDelete      = { item -> viewModel.removeItem(eventId, item.id) }
        )
        binding.rvItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = itemAdapter
            isNestedScrollingEnabled = false
        }

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
            pickPostImages.launch("image/*")
        }

        binding.btnAddItem.setOnClickListener { showAddItemDialog() }
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

                                // Miembros (horizontal RV con avatares)
                                currentMembers = state.members
                                memberAdapter.submitList(state.members)

                                // Items
                                itemAdapter.updateMemberNames(state.memberNames)
                                val items = state.event.items
                                binding.tvEmptyItems.isVisible = items.isEmpty()
                                binding.rvItems.isVisible = items.isNotEmpty()
                                itemAdapter.submitList(items)

                                // Cuentas
                                bindSettlements(viewModel.computeSettlements(items), state.memberNames)

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

        if (!event.imageURL.isNullOrBlank()) {
            binding.ivEventImage.isVisible = true
            Glide.with(this).load(event.imageURL).centerCrop().into(binding.ivEventImage)
        } else {
            binding.ivEventImage.isVisible = false
        }

        binding.tvToolbarTitle.text = event.title ?: "Evento"
        binding.tvEventEmoji.text = if (event.type == EventType.SECRET) "\uD83D\uDD2E" else "\uD83D\uDE80"
        binding.tvEventTitle.text = event.title ?: ""
        binding.tvEventDate.text = event.date?.let { fmt.format(it) } ?: "Sin fecha"
        binding.tvEventLocation.text = event.location?.ifBlank { "Sin ubicacion" } ?: "Sin ubicacion"
        binding.tvEventDescription.text = event.description?.ifBlank { "" } ?: ""
        binding.tvEventDescription.isVisible = !event.description.isNullOrBlank()
        binding.tvMemberCount.text = "${event.memberIds.size} astronautas"

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

    // ── Dialog de item ──────────────────────────────────────────────────────

    private fun showAddItemDialog() {
        val members = currentMembers
        if (members.isEmpty()) return
        val ctx = requireContext()

        val scroll = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 8)
        }
        scroll.addView(container)

        val etName = EditText(ctx).apply {
            hint = "Nombre del item"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x55FFFFFF)
        }

        val etPrice = EditText(ctx).apply {
            hint = "Precio total (€)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x55FFFFFF)
            setPadding(0, 24, 0, 0)
        }

        val tvPayerLabel = TextView(ctx).apply {
            text = "Quién paga:"
            setTextColor(0x88FFFFFF.toInt())
            textSize = 12f
            setPadding(0, 24, 0, 6)
        }

        val spinnerPayer = Spinner(ctx)
        spinnerPayer.adapter = ArrayAdapter(
            ctx,
            android.R.layout.simple_spinner_dropdown_item,
            members.map { it.username ?: "?" }
        )
        // Preseleccionar al usuario actual
        val selfIndex = members.indexOfFirst { it.id == userId }.coerceAtLeast(0)
        spinnerPayer.setSelection(selfIndex)

        val tvSplitLabel = TextView(ctx).apply {
            text = "Dividir entre:"
            setTextColor(0x88FFFFFF.toInt())
            textSize = 12f
            setPadding(0, 20, 0, 4)
        }

        val checkBoxes = members.map { member ->
            CheckBox(ctx).apply {
                text = member.username ?: "?"
                isChecked = true
                setTextColor(0xCCFFFFFF.toInt())
            }
        }

        container.addView(etName)
        container.addView(etPrice)
        container.addView(tvPayerLabel)
        container.addView(spinnerPayer)
        container.addView(tvSplitLabel)
        checkBoxes.forEach { container.addView(it) }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Añadir item")
            .setView(scroll)
            .setPositiveButton("Añadir") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                val price = etPrice.text.toString().toDoubleOrNull() ?: 0.0
                val payerId = members.getOrNull(spinnerPayer.selectedItemPosition)?.id ?: userId
                val splitIds = checkBoxes.indices
                    .filter { checkBoxes[it].isChecked }
                    .mapNotNull { members.getOrNull(it)?.id }
                    .ifEmpty { listOf(userId) }

                viewModel.addItem(
                    eventId,
                    EventItem(
                        id                  = UUID.randomUUID().toString(),
                        name                = name,
                        price               = price,
                        paidByUserId        = payerId,
                        splitBetweenUserIds = splitIds
                    )
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ── Cuentas (Tricount) ──────────────────────────────────────────────────

    private fun bindSettlements(settlements: List<Settlement>, memberNames: Map<String, String>) {
        binding.cardSettlements.isVisible = settlements.isNotEmpty()
        binding.layoutSettlements.removeAllViews()
        settlements.forEach { s ->
            val fromName = memberNames[s.fromId] ?: s.fromId
            val toName   = memberNames[s.toId]   ?: s.toId
            val tv = TextView(requireContext()).apply {
                text = "$fromName  →  $toName    ${"%.2f".format(s.amount)}€"
                setTextColor(0xCCFFFFFF.toInt())
                textSize = 13f
                setPadding(0, 10, 0, 10)
            }
            binding.layoutSettlements.addView(tv)

            val divider = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(0x1AFFFFFF)
            }
            binding.layoutSettlements.addView(divider)
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
