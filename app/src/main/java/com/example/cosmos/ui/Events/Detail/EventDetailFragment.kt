package com.example.cosmos.ui.Events

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
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
import android.content.Intent
import android.net.Uri
import com.example.cosmos.ui.Events.Detail.DeleteEventUiState
import com.example.cosmos.ui.Events.Detail.EventDetailUiState
import com.example.cosmos.ui.Events.Detail.EventDetailViewModel
import com.example.cosmos.ui.Events.Detail.EventItemAdapter
import com.example.cosmos.ui.Events.Detail.EventMemberAdapter
import com.example.cosmos.ui.Events.Detail.ForumThreadAdapter
import com.example.cosmos.ui.Events.Detail.PostUiState
import com.example.cosmos.ui.Events.Detail.RateUiState
import com.example.cosmos.ui.Events.Detail.Settlement
import com.google.android.material.bottomsheet.BottomSheetDialog
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

    // Publicar en News — bottom sheet
    private var publishSheet: BottomSheetDialog? = null
    private var publishPhotoContainer: LinearLayout? = null
    private var pendingPublishUris: MutableList<Uri> = mutableListOf()

    private val pickPostImages = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        pendingPublishUris.addAll(uris)
        uris.forEach { uri -> addPhotoThumb(uri) }
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
            val state = viewModel.uiState.value as? EventDetailUiState.Success ?: return@setOnClickListener
            showPublishSheet(state)
        }

        binding.btnAddItem.setOnClickListener { showAddItemDialog() }

        binding.btnEditEvent.setOnClickListener {
            val bundle = Bundle().apply { putString("editEventId", eventId) }
            findNavController().navigate(R.id.action_eventDetailFragment_to_createEventFragment, bundle)
        }

        binding.btnDeleteEvent.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar evento")
                .setMessage("Esta accion es irreversible. Se eliminara el evento para todos los miembros.")
                .setPositiveButton("Eliminar") { _, _ -> viewModel.deleteEvent(eventId) }
                .setNegativeButton("Cancelar", null)
                .show()
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
                            is PostUiState.Loading -> {
                                publishSheet?.findViewById<LinearLayout>(R.id.layoutPublishing)?.isVisible = true
                                publishSheet?.findViewById<TextView>(R.id.btnPublish)?.isEnabled = false
                            }
                            is PostUiState.Success -> {
                                publishSheet?.dismiss()
                                publishSheet = null
                                pendingPublishUris.clear()
                                Snackbar.make(binding.root, "Transmisión publicada", Snackbar.LENGTH_SHORT).show()
                                viewModel.resetPostState()
                            }
                            is PostUiState.Error -> {
                                publishSheet?.findViewById<LinearLayout>(R.id.layoutPublishing)?.isVisible = false
                                publishSheet?.findViewById<TextView>(R.id.btnPublish)?.isEnabled = true
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetPostState()
                            }
                            else -> {}
                        }
                    }
                }
                launch {
                    viewModel.deleteState.collect { state ->
                        when (state) {
                            is DeleteEventUiState.Success -> {
                                viewModel.resetDeleteState()
                                findNavController().popBackStack()
                            }
                            is DeleteEventUiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetDeleteState()
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
        val location = event.location?.ifBlank { null }
        binding.tvEventLocation.text = location ?: "Sin ubicacion"
        binding.btnOpenMap.isVisible = location != null
        binding.btnOpenMap.setOnClickListener {
            val query = location ?: return@setOnClickListener
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://maps.google.com/?q=${Uri.encode(query)}")))
            }
        }
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

        // Botones de admin en toolbar
        binding.btnEditEvent.isVisible   = isAdmin
        binding.btnDeleteEvent.isVisible = isAdmin

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

    // ── Bottom sheet publicar en News (estilo Instagram) ──────────────────────

    private fun showPublishSheet(state: EventDetailUiState.Success) {
        pendingPublishUris.clear()

        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottomsheet_publish_post, null)
        dialog.setContentView(view)
        publishSheet = dialog

        val etComment       = view.findViewById<EditText>(R.id.etPostComment)
        val tvStars         = view.findViewById<TextView>(R.id.tvPostStars)
        val tvRating        = view.findViewById<TextView>(R.id.tvPostRating)
        val tvEventTitle    = view.findViewById<TextView>(R.id.tvPostEventTitle)
        val btnPublish      = view.findViewById<TextView>(R.id.btnPublish)
        val btnAddPhoto     = view.findViewById<FrameLayout>(R.id.btnAddPhoto)
        val llPhotos        = view.findViewById<LinearLayout>(R.id.llPhotos)
        publishPhotoContainer = llPhotos

        // Pre-fill rating
        val rate = state.userRate
        if (rate != null) {
            val filled = rate.rating.toInt().coerceIn(0, 5)
            tvStars.text = "\u2605".repeat(filled) + "\u2606".repeat(5 - filled)
            tvStars.setTextColor(0xFFFFD700.toInt())
            tvRating.text = "%.1f".format(rate.rating)
            etComment.setText(rate.comment ?: "")
        }
        tvEventTitle.text = state.event.title ?: ""

        btnAddPhoto.setOnClickListener { pickPostImages.launch("image/*") }

        btnPublish.setOnClickListener {
            val comment = etComment.text.toString().trim()
            viewModel.publishPost(eventId, userId, comment, pendingPublishUris.toList())
        }

        dialog.setOnDismissListener {
            publishSheet = null
            publishPhotoContainer = null
        }

        dialog.show()
    }

    private fun addPhotoThumb(uri: Uri) {
        val container = publishPhotoContainer ?: return
        val ctx = requireContext()
        val size = (80 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()

        val frame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).also { it.setMargins(0, 0, margin, 0) }
        }
        val iv = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0x22FFFFFF)
        }
        Glide.with(this).load(uri).centerCrop().into(iv)
        frame.addView(iv)

        // Tap para eliminar
        frame.setOnClickListener {
            pendingPublishUris.remove(uri)
            container.removeView(frame)
        }

        // Insertar antes del botón "+"
        container.addView(frame, 0)
    }

    // ── Bottom sheet de item ─────────────────────────────────────────────────

    private fun showAddItemDialog() {
        val members = currentMembers
        if (members.isEmpty()) return
        val ctx = requireContext()

        val dialog = BottomSheetDialog(ctx)
        val view = layoutInflater.inflate(R.layout.bottomsheet_add_item, null)
        dialog.setContentView(view)

        val etName    = view.findViewById<EditText>(R.id.etItemName)
        val etPrice   = view.findViewById<EditText>(R.id.etItemPrice)
        val llPayer   = view.findViewById<LinearLayout>(R.id.llPayerChips)
        val llSplit   = view.findViewById<LinearLayout>(R.id.llSplitChips)
        val btnConfirm = view.findViewById<LinearLayout>(R.id.btnConfirmItem)

        val density   = resources.displayMetrics.density
        val chipH     = (36 * density).toInt()
        val chipPadH  = (14 * density).toInt()
        val chipMargin = (8 * density).toInt()

        // ── Payer chips (radio-style, one selected at a time) ─────────────────
        var selectedPayerIndex = members.indexOfFirst { it.id == userId }.coerceAtLeast(0)

        val payerChips = members.map { member ->
            TextView(ctx).apply {
                text = member.username ?: "?"
                gravity = Gravity.CENTER_VERTICAL
                setPadding(chipPadH, 0, chipPadH, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, chipH
                ).also { it.setMargins(0, 0, chipMargin, 0) }
                textSize = 12f
            }
        }

        fun refreshPayerStyles() {
            payerChips.forEachIndexed { i, chip ->
                if (i == selectedPayerIndex) {
                    chip.setBackgroundResource(R.drawable.bg_tab_selected)
                    chip.setTextColor(0xFFC4BCFF.toInt())
                } else {
                    chip.setBackgroundResource(R.drawable.bg_chip_glass)
                    chip.setTextColor(0x88FFFFFF.toInt())
                }
            }
        }
        payerChips.forEachIndexed { i, chip ->
            chip.setOnClickListener { selectedPayerIndex = i; refreshPayerStyles() }
            llPayer.addView(chip)
        }
        refreshPayerStyles()

        // ── Split chips (multi-select toggle, all on initially) ───────────────
        val splitSelected = BooleanArray(members.size) { true }

        val splitChips = members.map { member ->
            TextView(ctx).apply {
                text = member.username ?: "?"
                gravity = Gravity.CENTER_VERTICAL
                setPadding(chipPadH, 0, chipPadH, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, chipH
                ).also { it.setMargins(0, 0, chipMargin, 0) }
                textSize = 12f
            }
        }

        fun refreshSplitStyles() {
            splitChips.forEachIndexed { i, chip ->
                if (splitSelected[i]) {
                    chip.setBackgroundResource(R.drawable.bg_tab_selected)
                    chip.setTextColor(0xFFC4BCFF.toInt())
                } else {
                    chip.setBackgroundResource(R.drawable.bg_chip_glass)
                    chip.setTextColor(0x88FFFFFF.toInt())
                }
            }
        }
        splitChips.forEachIndexed { i, chip ->
            chip.setOnClickListener { splitSelected[i] = !splitSelected[i]; refreshSplitStyles() }
            llSplit.addView(chip)
        }
        refreshSplitStyles()

        // ── Confirm ───────────────────────────────────────────────────────────
        btnConfirm.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isBlank()) return@setOnClickListener
            val price   = etPrice.text.toString().toDoubleOrNull() ?: 0.0
            val payerId = members.getOrNull(selectedPayerIndex)?.id ?: userId
            val splitIds = splitSelected.indices
                .filter { splitSelected[it] }
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
            dialog.dismiss()
        }

        dialog.show()
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
