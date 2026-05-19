package com.example.cosmos.ui.Events

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  readOnly (argumento Bundle)
 *      Cuando se navega desde News (ver post de otro usuario), el
 *      Fragment se abre en modo solo lectura: no se puede valorar,
 *      publicar, ni interactuar con el foro. Se pasa como Boolean
 *      en el Bundle.
 *
 *  isPending (estado del ViewModel)
 *      Si el usuario está en pendingIds del evento (invitado pero no
 *      aceptado), se muestra una card de invitación con aceptar/rechazar.
 *      Todo lo interactivo se oculta hasta que acepte.
 *
 *  Flujo post-evento: finished -> rate -> publish
 *      1. Admin finaliza el evento (btnFinishEvent)
 *      2. Cada miembro puede valorar (btnRate -> showRateDialog)
 *      3. Tras valorar, puede publicar en News (btnPublishPost -> showPublishSheet)
 *      Los botones se muestran/ocultan según el progreso en bindFinishState().
 *
 *  Mini-foro (threads + replies)
 *      ForumThreadAdapter muestra los hilos. Al tocar "Responder",
 *      replyingToThreadId guarda a qué hilo se responde. btnSend
 *      usa ese ID para decidir si crear un thread nuevo o una reply.
 *
 *  EventItems + Settlements (cuentas)
 *      Cada evento puede tener items con precio, pagador y reparto.
 *      computeSettlements() calcula quién debe a quién (estilo Tricount).
 *      Se muestra en bindSettlements() como una lista de deudas.
 *
 *  publishSheet con fotos
 *      pickPostImages usa GetMultipleContents para seleccionar fotos.
 *      Las URIs se guardan en pendingPublishUris y se suben al publicar.
 *      addPhotoThumb() genera miniaturas con Glide. Tap para eliminar.
 * ═══════════════════════════════════════════════════════════════════
 */

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
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
    private var readOnly: Boolean = false
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
        readOnly = arguments?.getBoolean("readOnly", false) ?: false

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
            onDelete      = { item -> viewModel.removeItem(eventId, item.id) },
            readOnly      = readOnly
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
            showConfirmDialog(
                title = getString(R.string.confirm_finish_title),
                message = getString(R.string.confirm_finish_msg),
                confirmText = getString(R.string.confirm_finish_btn)
            ) { viewModel.finishEvent(eventId) }
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
            showConfirmDialog(
                title = getString(R.string.confirm_delete_title),
                message = getString(R.string.confirm_delete_msg),
                confirmText = getString(R.string.confirm_delete_btn)
            ) { viewModel.deleteEvent(eventId) }
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
                                bindFinishState(state.event, state.canFinish, state.hasRated, state.hasPosted, state.isMember)
                                bindPendingState(state.isPending, state.memberNames, state.event.adminIds)

                                // Items en modo reducido para no-miembros o readOnly
                                itemAdapter.readOnly = readOnly || !state.isMember || state.isPending

                                // Miembros (horizontal RV con avatares)
                                currentMembers = state.members
                                memberAdapter.submitList(state.members)

                                // Items
                                itemAdapter.updateMemberNames(state.memberNames)
                                val items = state.event.items
                                binding.tvEmptyItems.isVisible = items.isEmpty()
                                binding.rvItems.isVisible = items.isNotEmpty()
                                itemAdapter.submitList(items)

                                // Total de items
                                val total = items.sumOf { it.price }
                                val hasItems = items.isNotEmpty() && total > 0
                                binding.layoutTotal.isVisible = hasItems
                                binding.dividerTotal.isVisible = hasItems
                                if (hasItems) binding.tvItemsTotal.text = "%.2f€".format(total)

                                // Cuentas (solo para miembros, no en readOnly)
                                if (!readOnly && state.isMember && !state.isPending) {
                                    bindSettlements(viewModel.computeSettlements(items), state.memberNames)
                                } else {
                                    binding.cardSettlements.isVisible = false
                                }

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
        val mapAddr = event.mapAddress?.ifBlank { null }
        val mapQuery = mapAddr
        binding.tvEventLocation.text = location ?: "Sin ubicacion"
        binding.tvMapAddress.isVisible = mapAddr != null && location != null
        binding.tvMapAddress.text = mapAddr ?: ""
        binding.btnOpenMap.isVisible = mapQuery != null
        binding.btnOpenMap.setOnClickListener {
            val query = mapQuery ?: return@setOnClickListener
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
            binding.tvDuration.text = getString(R.string.duration_approx, label)
        } else {
            binding.tvDuration.isVisible = false
        }
    }

    private fun bindFinishState(
        event: Event, canFinish: Boolean, hasRated: Boolean,
        hasPosted: Boolean = false, isMember: Boolean = true
    ) {
        val isAdmin = event.adminIds.contains(userId)
        val canEdit = isAdmin && isMember && !readOnly

        // Botones de admin en toolbar
        binding.btnEditEvent.isVisible   = canEdit
        binding.btnDeleteEvent.isVisible = canEdit

        // Boton finalizar: solo admin + tiempo pasado + no finalizado
        binding.btnFinishEvent.isVisible = canEdit && canFinish && !event.finished

        // Items: ocultar boton "+" en modo solo lectura o no-miembro
        binding.btnAddItem.isVisible = isMember && !readOnly

        // Foro: ocultar input en modo solo lectura o no-miembro
        binding.layoutBottom.isVisible = isMember && !readOnly

        // Card mision completada
        binding.cardFinished.isVisible = event.finished && isMember && !readOnly
        if (event.finished && isMember && !readOnly) {
            binding.btnRate.isVisible = !hasRated
            binding.tvAlreadyRated.isVisible = hasRated
            binding.btnPublishPost.isVisible = hasRated && !hasPosted
            binding.tvAlreadyPosted.isVisible = hasPosted
        }
    }

    private fun bindPendingState(isPending: Boolean, memberNames: Map<String, String> = emptyMap(), adminIds: List<String> = emptyList()) {
        binding.cardPendingInvite.isVisible = isPending
        if (isPending) {
            // Mostrar quién invitó
            val inviterName = adminIds.firstOrNull()?.let { memberNames[it] }
            binding.tvPendingInviteLabel.text = if (!inviterName.isNullOrBlank())
                "@$inviterName TE INVITA A ESTA MISION" else "TE HAN INVITADO A ESTA MISION"

            // Ocultar todo lo interactivo: solo ver info + aceptar/rechazar
            binding.layoutBottom.isVisible = false
            binding.btnAddItem.isVisible = false
            binding.btnEditEvent.isVisible = false
            binding.btnDeleteEvent.isVisible = false
            binding.btnFinishEvent.isVisible = false
            binding.cardFinished.isVisible = false

            binding.btnAcceptInvite.setOnClickListener {
                viewModel.acceptEventInvite(eventId, userId)
            }
            binding.btnRejectInvite.setOnClickListener {
                viewModel.rejectEventInvite(eventId, userId)
                findNavController().popBackStack()
            }
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

        // Chips de visibilidad (toggle)
        val chipLocation    = view.findViewById<TextView>(R.id.chipShowLocation)
        val chipDescription = view.findViewById<TextView>(R.id.chipShowDescription)
        val chipItems       = view.findViewById<TextView>(R.id.chipShowItems)

        var showLocation    = true
        var showDescription = true
        var showItems       = false

        fun styleChip(chip: TextView, active: Boolean) {
            if (active) {
                chip.setBackgroundResource(R.drawable.bg_tab_selected)
                chip.setTextColor(0xFFC4BCFF.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_glass)
                chip.setTextColor(0x88FFFFFF.toInt())
            }
        }

        // Estado inicial
        styleChip(chipLocation, showLocation)
        styleChip(chipDescription, showDescription)
        styleChip(chipItems, showItems)

        // Ocultar chip items si no hay items
        chipItems.isVisible = state.event.items.isNotEmpty()

        chipLocation.setOnClickListener {
            showLocation = !showLocation; styleChip(chipLocation, showLocation)
        }
        chipDescription.setOnClickListener {
            showDescription = !showDescription; styleChip(chipDescription, showDescription)
        }
        chipItems.setOnClickListener {
            showItems = !showItems; styleChip(chipItems, showItems)
        }

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
            viewModel.publishPost(
                eventId, userId, comment, pendingPublishUris.toList(),
                showLocation, showDescription, showItems
            )
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

    // ── Bottom sheet de valoracion ──────────────────────────────────────────

    private fun showRateDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottomsheet_rate, null)
        dialog.setContentView(view)

        val ratingBar   = view.findViewById<RatingBar>(R.id.ratingBar)
        val tvValue     = view.findViewById<TextView>(R.id.tvRatingValue)
        val etComment   = view.findViewById<EditText>(R.id.etRateComment)
        val btnSubmit   = view.findViewById<LinearLayout>(R.id.btnSubmitRate)

        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            tvValue.text = "%.1f".format(rating)
        }

        btnSubmit.setOnClickListener {
            viewModel.submitRate(
                eventId = eventId,
                userId = userId,
                rating = ratingBar.rating,
                comment = etComment.text.toString().trim()
            )
            dialog.dismiss()
        }

        dialog.show()
    }

    // ── Dialog de confirmacion custom ────────────────────────────────────────

    private fun showConfirmDialog(
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_confirm, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.findViewById<TextView>(R.id.tvDialogTitle).text = title
        view.findViewById<TextView>(R.id.tvDialogMessage).text = message
        view.findViewById<TextView>(R.id.btnDialogConfirm).text = confirmText
        view.findViewById<TextView>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnDialogConfirm).setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
    }
}
