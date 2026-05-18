package com.example.cosmos.ui.Events.Home

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  repeatOnLifecycle(STARTED)
 *      Patrón seguro para observar StateFlows en un Fragment.
 *      "Repite" el bloque interno cada vez que el Fragment pasa a
 *      estado STARTED (visible) y lo cancela cuando pasa a STOPPED
 *      (va a segundo plano). Así no recibimos updates cuando la UI
 *      no está visible y evitamos memory leaks.
 *      viewLifecycleOwner (no lifecycleOwner) porque queremos el
 *      ciclo de vida de la VISTA, no del Fragment en sí.
 *
 *  Handler + Runnable (marquee y countdown)
 *      Para ejecutar código repetidamente con un intervalo de tiempo
 *      sin bloquear el hilo principal, usamos Handler.postDelayed().
 *      El Runnable se re-programa a sí mismo al final de su ejecución
 *      creando un bucle. Para parar el bucle: removeCallbacks().
 *      Importante guardar referencia al Runnable para poder pararlo.
 *
 *  activityViewModels (GroupViewModel)
 *      EventFragment necesita comunicarse con GroupFragment para
 *      abrirle el bottom sheet de solicitudes. Como no son padre/hijo
 *      en la jerarquía de navegación, comparten GroupViewModel a nivel
 *      de Activity. EventFragment escribe la señal, GroupFragment la lee.
 *
 *  setOnTouchListener retornando false
 *      Al retornar false en el touch listener del HorizontalScrollView,
 *      le decimos que procese el gesto normalmente (el usuario puede
 *      arrastrar). Nosotros solo "escuchamos" para saber cuándo
 *      parar y reanudar el auto-scroll.
 *
 *  AlertItem
 *      Modelo local (solo existe en esta pantalla) para representar
 *      una notificación en el marquee y en el bottom sheet de alertas.
 *      Puede ser una solicitud de amistad o un evento próximo.
 * ═══════════════════════════════════════════════════════════════════
 */

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cosmos.R
import com.example.cosmos.Model.Actions.FriendRequest
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.databinding.FragmentEventBinding
import com.example.cosmos.databinding.BottomSheetAlertsBinding
import com.example.cosmos.ui.Orbit.GroupViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class EventFragment : Fragment() {

    private var _binding: FragmentEventBinding? = null
    private val binding get() = _binding!!

    // viewModels() → instancia propia del Fragment, muere con él
    private val viewModel: EventViewModel by viewModels()
    // activityViewModels() → instancia compartida con GroupFragment para la señal de solicitudes
    private val groupViewModel: GroupViewModel by activityViewModels()

    private var currentUserId = ""

    private val eventAdapter = EventAdapter(
        onEventClick = { event ->
            val bundle = Bundle().apply { putString("eventId", event.id ?: "") }
            findNavController().navigate(R.id.action_eventFragment_to_eventDetailFragment, bundle)
        },
        onAcceptInvite = { event ->
            viewModel.acceptEventInvite(event.id ?: "", currentUserId)
        },
        onRejectInvite = { event ->
            viewModel.rejectEventInvite(event.id ?: "", currentUserId)
        }
    )

    // Handlers para los bucles de animación.
    // Se guardan como campos para poder cancelarlos en onDestroyView.
    private val marqueeHandler   = Handler(Looper.getMainLooper())
    private var marqueeRunnable: Runnable? = null
    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    // Runnable específico para reanudar el marquee tras soltarlo
    private var resumeMarqueeRunnable: Runnable? = null

    private var currentAlerts:   List<AlertItem>     = emptyList()
    private var currentEvents:   List<Event>          = emptyList()
    private var currentRequests: List<FriendRequest>  = emptyList()
    private var currentPendingAdminNames: Map<String, String> = emptyMap()
    private val dismissedAlertKeys = mutableSetOf<String>()

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

        currentUserId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        viewModel.loadEvents(currentUserId)
        viewModel.loadIncomingRequests(currentUserId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancelar todos los Runnables pendientes para no ejecutar código
        // sobre vistas que ya no existen
        stopMarquee()
        stopCountdown()
        resumeMarqueeRunnable?.let { marqueeHandler.removeCallbacks(it) }
        _binding = null
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private fun initUI() {
        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter        = eventAdapter
        }
    }

    private fun initListeners() {
        binding.fabCreateEvent.setOnClickListener {
            findNavController().navigate(R.id.action_eventFragment_to_createEventFragment)
        }

        binding.marqueeContainer.setOnClickListener {
            showAlertsBottomSheet()
        }

        // Touch listener en el HorizontalScrollView del marquee.
        // DOWN/MOVE: el usuario está arrastrando → paramos el auto-scroll para no interferir.
        // UP/CANCEL: soltó → programamos reanudar el auto-scroll 2 segundos después.
        // Retornamos false para que el HorizontalScrollView también procese el gesto
        // y el usuario pueda arrastrar libremente.
        binding.marqueeScroll.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    resumeMarqueeRunnable?.let { marqueeHandler.removeCallbacks(it) }
                    stopMarquee()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resumeMarqueeRunnable?.let { marqueeHandler.removeCallbacks(it) }
                    resumeMarqueeRunnable = Runnable {
                        if (_binding != null) startAutoScroll()
                    }
                    marqueeHandler.postDelayed(resumeMarqueeRunnable!!, 2000)
                }
            }
            false
        }

        binding.cardNextEvent.setOnClickListener {
            val nextEvent = getNextEvent(currentEvents)
            if (nextEvent != null) {
                val bundle = Bundle().apply { putString("eventId", nextEvent.id ?: "") }
                findNavController().navigate(R.id.action_eventFragment_to_eventDetailFragment, bundle)
            }
        }
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            // repeatOnLifecycle garantiza que dejamos de colectar cuando el Fragment
            // no es visible, y reanudamos automáticamente cuando vuelve a serlo
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Múltiples launch dentro de repeatOnLifecycle corren en PARALELO,
                // cada uno con su propio collector independiente
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state is EventUiState.Loading
                        binding.layoutEmpty.isVisible  = state is EventUiState.Empty
                        binding.rvEvents.isVisible     = state is EventUiState.Success

                        if (state is EventUiState.Success) {
                            currentEvents = state.events.filter { !it.finished }
                            currentPendingAdminNames = state.adminNames
                            val sorted = currentEvents.sortedBy { it.date?.time ?: Long.MAX_VALUE }
                            val pendingSorted = state.pendingEvents.sortedBy { it.date?.time ?: Long.MAX_VALUE }
                            val avatars = state.avatarUrls
                            val names = state.adminNames

                            val items = pendingSorted.map { EventListItem(it, isPending = true, avatarUrls = avatars, inviterName = names[it.adminIds.firstOrNull() ?: ""]) } +
                                        sorted.map { EventListItem(it, isPending = false, avatarUrls = avatars) }
                            eventAdapter.submitList(items)
                            setupNextEvent(sorted)
                            startCountdown()
                            updateHomeBadge(state.pendingEvents.size + currentRequests.size)
                            rebuildAlerts()
                        } else {
                            binding.cardNextEvent.isVisible = false
                            binding.tvNextUpLabel.isVisible = false
                        }
                    }
                }

                launch {
                    viewModel.incomingRequests.collect { requests ->
                        currentRequests = requests
                        val pendingCount = (viewModel.uiState.value as? EventUiState.Success)?.pendingEvents?.size ?: 0
                        updateHomeBadge(pendingCount + requests.size)
                        rebuildAlerts()
                    }
                }
            }
        }
    }

    // ── Próximo evento destacado ──────────────────────────────────────────────

    private fun setupNextEvent(sortedEvents: List<Event>) {
        val nextEvent = getNextEvent(sortedEvents)
        val hasNext   = nextEvent != null

        binding.cardNextEvent.isVisible = hasNext
        binding.tvNextUpLabel.isVisible = hasNext

        if (nextEvent == null) return

        binding.tvNextEventTitle.text    = nextEvent.title ?: "Sin título"
        binding.tvNextEventLocation.text = nextEvent.location ?: ""
        binding.tvNextEventLocation.isVisible = !nextEvent.location.isNullOrBlank()
        binding.tvNextEventCrew.text     = "${nextEvent.memberIds.size} crew members"

        val eventDate = nextEvent.date
        if (eventDate != null) {
            val dateFormat = SimpleDateFormat("EEEE dd MMM · HH:mm", Locale.getDefault())
            binding.tvNextEventDate.text = dateFormat.format(eventDate)
            updateNextCountdown(eventDate)

            val timeLeft   = eventDate.time - System.currentTimeMillis()
            val isImminent = timeLeft in 1..IMMINENT_MS
            // Color naranja si el evento es inminente (<24h), azul app_color si no
            if (isImminent) {
                binding.viewNextAccent.setBackgroundColor(Color.parseColor("#FF6B3D"))
                binding.tvNextCountdown.setTextColor(Color.parseColor("#FF6B3D"))
            } else {
                binding.viewNextAccent.setBackgroundColor(Color.parseColor("#1717AB"))
                binding.tvNextCountdown.setTextColor(Color.parseColor("#FFCC80"))
            }
        } else {
            binding.tvNextEventDate.text = getString(R.string.date_tbc)
            binding.tvNextCountdown.text = "---"
        }
    }

    private fun updateNextCountdown(eventDate: Date) {
        val timeLeft = eventDate.time - System.currentTimeMillis()
        if (timeLeft <= 0) {
            binding.tvNextCountdown.text       = getString(R.string.in_progress)
            binding.layoutNextCountdown.isVisible = true
            return
        }
        val days    = TimeUnit.MILLISECONDS.toDays(timeLeft)
        val hours   = TimeUnit.MILLISECONDS.toHours(timeLeft) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60

        binding.tvNextCountdown.text = when {
            days > 0  -> "T- ${days}d ${hours}h ${minutes}m"
            hours > 0 -> "T- ${hours}h ${minutes}m"
            else      -> "T- ${minutes}m"
        }
        binding.layoutNextCountdown.isVisible = true
    }

    // El próximo evento es el más cercano en el futuro (fecha > ahora)
    private fun getNextEvent(events: List<Event>): Event? {
        val now = System.currentTimeMillis()
        return events
            .filter { it.date != null && it.date.time > now }
            .minByOrNull { it.date!!.time }
    }

    // ── Countdown timer ───────────────────────────────────────────────────────

    // Bucle que actualiza el countdown cada minuto.
    // El Runnable se re-programa a sí mismo con postDelayed para crear el bucle.
    private fun startCountdown() {
        stopCountdown()
        countdownRunnable = object : Runnable {
            override fun run() {
                if (_binding == null) return  // Fragment destruido, no hacer nada
                val nextEvent = getNextEvent(currentEvents)
                if (nextEvent?.date != null) updateNextCountdown(nextEvent.date)
                eventAdapter.notifyDataSetChanged()
                countdownHandler.postDelayed(this, 60_000)
            }
        }
        countdownHandler.postDelayed(countdownRunnable!!, 60_000)
    }

    private fun stopCountdown() {
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        countdownRunnable = null
    }

    // ── Alertas ───────────────────────────────────────────────────────────────

    // Reconstruye la lista de alertas combinando solicitudes de amistad pendientes
    // y eventos próximos (inminentes <24h y próximos <3 días).
    // Se llama cada vez que cambian los eventos o las solicitudes.
    private fun rebuildAlerts() {
        val now    = System.currentTimeMillis()
        val alerts = mutableListOf<AlertItem>()

        // Solicitudes de amistad: una alerta agrupada si hay varias
        if (currentRequests.isNotEmpty()) {
            if (currentRequests.size == 1) {
                val req = currentRequests.first()
                alerts.add(AlertItem(
                    iconRes   = R.drawable.ic_person_add,
                    title     = getString(R.string.alert_friend_want, req.fromUsername),
                    subtitle  = getString(R.string.alert_friend_request),
                    timeLabel = "",
                    alertKey  = "friend_requests",
                    type      = AlertType.FRIEND_REQUEST
                ))
            } else {
                alerts.add(AlertItem(
                    iconRes   = R.drawable.ic_person_add,
                    title     = getString(R.string.alert_friend_requests_count, currentRequests.size),
                    subtitle  = currentRequests.take(3).joinToString(", ") { it.fromUsername },
                    timeLabel = "",
                    alertKey  = "friend_requests",
                    type      = AlertType.FRIEND_REQUEST
                ))
            }
        }

        // Invitaciones a eventos pendientes
        val pendingEvts = (viewModel.uiState.value as? EventUiState.Success)?.pendingEvents ?: emptyList()
        for (pending in pendingEvts) {
            val inviterName = currentPendingAdminNames[pending.adminIds.firstOrNull() ?: ""] ?: getString(R.string.alert_someone)
            alerts.add(AlertItem(
                iconRes   = R.drawable.ic_rocket,
                title     = getString(R.string.alert_invites_you, inviterName, pending.title ?: getString(R.string.an_event)),
                subtitle  = getString(R.string.alert_pending_invite),
                timeLabel = "",
                eventId   = pending.id,
                alertKey  = "pending_${pending.id}",
                type      = AlertType.EVENT_INVITE
            ))
        }

        // Eventos próximos (solo los que aún no han pasado, ordenados por fecha)
        val sortedEvents = currentEvents
            .filter { it.date != null && it.date.time > now }
            .sortedBy { it.date!!.time }

        for (event in sortedEvents) {
            val date     = event.date ?: continue
            val timeLeft = date.time - now

            val iconRes: Int
            val subtitle: String
            val timeLabel: String
            val alertType: AlertType

            when {
                timeLeft <= IMMINENT_MS -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(timeLeft)
                    val mins  = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60
                    iconRes   = R.drawable.ic_alert_urgent
                    subtitle  = getString(R.string.alert_imminent_launch)
                    timeLabel = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    alertType = AlertType.IMMINENT
                }
                timeLeft <= 3 * 24 * 60 * 60 * 1000L -> {
                    val days  = TimeUnit.MILLISECONDS.toDays(timeLeft)
                    iconRes   = R.drawable.ic_signal
                    subtitle  = if (days <= 1) getString(R.string.alert_tomorrow) else getString(R.string.alert_in_days, days)
                    timeLabel = "${days}d"
                    alertType = AlertType.UPCOMING
                }
                else -> continue
            }
            alerts.add(AlertItem(iconRes = iconRes, title = event.title ?: getString(R.string.event_fallback),
                subtitle = subtitle, timeLabel = timeLabel, eventId = event.id,
                alertKey = "event_${event.id}", type = alertType))
        }

        // Eventos lejanos (>3 días): aparecen en el marquee como info general
        for (event in currentEvents) {
            val date = event.date
            if (date != null && date.time > now && (date.time - now) > 3 * 24 * 60 * 60 * 1000L) {
                alerts.add(AlertItem(
                    iconRes   = R.drawable.ic_rocket,
                    title     = event.title ?: getString(R.string.alert_new_event),
                    subtitle  = "${event.memberIds.size} crew · ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)}",
                    timeLabel = "",
                    eventId   = event.id,
                    alertKey  = "event_${event.id}",
                    type      = AlertType.INFO
                ))
            }
        }

        currentAlerts = alerts.filter { it.alertKey !in dismissedAlertKeys }
        updateMarquee()
    }

    // Construye la lista con cabeceras de sección agrupando alertas por tipo.
    // Orden: solicitudes/invitaciones → inminentes → próximos → info
    private fun buildSectionedList(alerts: List<AlertItem>): List<AlertListItem> {
        val result = mutableListOf<AlertListItem>()
        val grouped = alerts.groupBy { it.type }

        // Agrupar FRIEND_REQUEST y EVENT_INVITE bajo la misma sección "INCOMING"
        val incomingItems = (grouped[AlertType.FRIEND_REQUEST] ?: emptyList()) +
                            (grouped[AlertType.EVENT_INVITE] ?: emptyList())
        if (incomingItems.isNotEmpty()) {
            val dotColor = AlertType.FRIEND_REQUEST.dotColor
            result.add(AlertListItem.Section(getString(R.string.alert_section_incoming), incomingItems.size, dotColor))
            incomingItems.forEach { result.add(AlertListItem.Alert(it)) }
        }

        // Resto de secciones
        val remainingTypes = listOf(
            AlertType.IMMINENT  to getString(R.string.alert_section_imminent),
            AlertType.UPCOMING  to getString(R.string.alert_section_upcoming),
            AlertType.INFO      to getString(R.string.alert_section_scheduled)
        )
        for ((type, title) in remainingTypes) {
            val items = grouped[type] ?: continue
            result.add(AlertListItem.Section(title, items.size, type.dotColor))
            items.forEach { result.add(AlertListItem.Alert(it)) }
        }

        return result
    }

    // ── Marquee ───────────────────────────────────────────────────────────────

    private fun updateMarquee() {
        if (_binding == null) return

        if (currentAlerts.isEmpty()) {
            binding.tvMarquee.text        = getString(R.string.orbit_stable)
            binding.tvMarqueeBadge.isVisible = false
            startAutoScroll()
            return
        }

        // Duplicamos el texto para que el scroll parezca infinito:
        // cuando llega al final del primer bloque, ya está leyendo el segundo
        // y al hacer scrollX = 0 el salto es imperceptible
        val marqueeText = currentAlerts.joinToString("     \u2022     ") { alert ->
            val time = if (alert.timeLabel.isNotEmpty()) " (${alert.timeLabel})" else ""
            "${alert.title} - ${alert.subtitle}$time"
        }
        binding.tvMarquee.text        = "$marqueeText     \u2022     $marqueeText"
        binding.tvMarqueeBadge.isVisible = true
        binding.tvMarqueeBadge.text   = "${currentAlerts.size}"

        startAutoScroll()
    }

    private fun startAutoScroll() {
        stopMarquee()
        val scrollView = binding.marqueeScroll
        scrollView.post {
            if (_binding == null) return@post
            val maxScroll = binding.tvMarquee.width - scrollView.width
            if (maxScroll <= 0) return@post

            // Empezamos desde la posición actual (no desde 0) para que
            // la reanudación tras arrastrar sea suave
            val startX = scrollView.scrollX

            val halfText = binding.tvMarquee.width / 2

            marqueeRunnable = object : Runnable {
                var scrollX = startX
                override fun run() {
                    if (_binding == null) return
                    scrollX += 2
                    // Al llegar al inicio del segundo bloque de texto (mitad del
                    // ancho total del TextView), volvemos a 0. Como ambos bloques
                    // son idénticos, el salto es invisible para el usuario.
                    if (scrollX >= halfText) scrollX = 0
                    scrollView.scrollTo(scrollX, 0)
                    marqueeHandler.postDelayed(this, 35)
                }
            }
            marqueeHandler.postDelayed(marqueeRunnable!!, 1000)
        }
    }

    private fun stopMarquee() {
        marqueeRunnable?.let { marqueeHandler.removeCallbacks(it) }
        marqueeRunnable = null
    }

    // ── Bottom sheet alertas ──────────────────────────────────────────────────

    private fun showAlertsBottomSheet() {
        val dialog       = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetAlertsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val hasAlerts = currentAlerts.isNotEmpty()
        sheetBinding.layoutNoAlerts.isVisible = !hasAlerts
        sheetBinding.rvAlerts.isVisible       = hasAlerts
        sheetBinding.btnClearAll.isVisible    = hasAlerts
        sheetBinding.tvAlertCount.isVisible   = hasAlerts
        if (hasAlerts) sheetBinding.tvAlertCount.text = "${currentAlerts.size}"

        if (hasAlerts) {
            val alertAdapter = AlertAdapter(
                showDismiss = true,
                onAlertClick = { alert ->
                    dialog.dismiss()
                    when (alert.type) {
                        AlertType.FRIEND_REQUEST -> {
                            activity?.findViewById<BottomNavigationView>(R.id.bottom_menu)
                                ?.selectedItemId = R.id.navigation_orbit
                            groupViewModel.signalOpenRequests()
                        }
                        else -> {
                            if (alert.eventId != null) {
                                val bundle = Bundle().apply { putString("eventId", alert.eventId) }
                                findNavController().navigate(
                                    R.id.action_eventFragment_to_eventDetailFragment, bundle
                                )
                            }
                        }
                    }
                },
                onDismiss = { alert ->
                    if (alert.alertKey.isNotEmpty()) {
                        dismissedAlertKeys.add(alert.alertKey)
                        rebuildAlerts()
                        val sectionedList = buildSectionedList(currentAlerts)
                        sheetBinding.layoutNoAlerts.isVisible = currentAlerts.isEmpty()
                        sheetBinding.rvAlerts.isVisible       = currentAlerts.isNotEmpty()
                        sheetBinding.btnClearAll.isVisible    = currentAlerts.isNotEmpty()
                        sheetBinding.tvAlertCount.isVisible   = currentAlerts.isNotEmpty()
                        if (currentAlerts.isNotEmpty()) sheetBinding.tvAlertCount.text = "${currentAlerts.size}"
                        (sheetBinding.rvAlerts.adapter as? AlertAdapter)?.submitList(sectionedList)
                    }
                }
            )
            sheetBinding.rvAlerts.layoutManager = LinearLayoutManager(requireContext())
            sheetBinding.rvAlerts.adapter        = alertAdapter
            alertAdapter.submitList(buildSectionedList(currentAlerts))

            sheetBinding.btnClearAll.setOnClickListener {
                currentAlerts.forEach { dismissedAlertKeys.add(it.alertKey) }
                rebuildAlerts()
                sheetBinding.layoutNoAlerts.isVisible = true
                sheetBinding.rvAlerts.isVisible       = false
                sheetBinding.btnClearAll.isVisible    = false
                sheetBinding.tvAlertCount.isVisible   = false
                alertAdapter.submitList(emptyList())
            }
        }

        dialog.show()
    }

    private fun updateHomeBadge(count: Int) {
        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_menu) ?: return
        val badge = bottomNav.getOrCreateBadge(R.id.navigation_home)
        if (count > 0) {
            badge.isVisible = true
            badge.number = count
            badge.backgroundColor = 0xFF7C6DF0.toInt()
        } else {
            bottomNav.removeBadge(R.id.navigation_home)
        }
    }

    companion object {
        // 24 horas en milisegundos. Umbral para considerar un evento "inminente"
        private const val IMMINENT_MS = 24 * 60 * 60 * 1000L
    }
}
