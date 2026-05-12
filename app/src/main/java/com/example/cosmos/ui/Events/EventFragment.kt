package com.example.cosmos.ui.Events

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
import com.example.cosmos.ui.Events.rvEvents.EventAdapter
import com.example.cosmos.ui.Events.rvEvents.EventUiState
import com.example.cosmos.ui.Events.rvEvents.EventViewModel
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

    private val eventAdapter = EventAdapter { event ->
        val bundle = Bundle().apply { putString("eventId", event.id ?: "") }
        findNavController().navigate(R.id.action_eventFragment_to_eventDetailFragment, bundle)
    }

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

        val userId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        viewModel.loadEvents(userId)
        viewModel.loadIncomingRequests(userId)
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
                            val sorted = currentEvents.sortedBy { it.date?.time ?: Long.MAX_VALUE }
                            eventAdapter.submitList(sorted)
                            setupNextEvent(sorted)
                            startCountdown()
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
            binding.tvNextEventDate.text = "Fecha por confirmar"
            binding.tvNextCountdown.text = "---"
        }
    }

    private fun updateNextCountdown(eventDate: Date) {
        val timeLeft = eventDate.time - System.currentTimeMillis()
        if (timeLeft <= 0) {
            binding.tvNextCountdown.text       = "EN CURSO"
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
                    title     = "${req.fromUsername} quiere ser tu amigo",
                    subtitle  = "Solicitud de amistad pendiente",
                    timeLabel = "",
                    alertKey  = "friend_requests"
                ))
            } else {
                alerts.add(AlertItem(
                    iconRes   = R.drawable.ic_person_add,
                    title     = "${currentRequests.size} solicitudes de amistad",
                    subtitle  = currentRequests.take(3).joinToString(", ") { it.fromUsername },
                    timeLabel = "",
                    alertKey  = "friend_requests"
                ))
            }
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

            when {
                timeLeft <= IMMINENT_MS -> {
                    // Menos de 24h: alerta urgente con icono rojo
                    val hours = TimeUnit.MILLISECONDS.toHours(timeLeft)
                    val mins  = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60
                    iconRes   = R.drawable.ic_alert_urgent
                    subtitle  = "Lanzamiento inminente"
                    timeLabel = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                }
                timeLeft <= 3 * 24 * 60 * 60 * 1000L -> {
                    // Entre 24h y 3 días: alerta moderada
                    val days  = TimeUnit.MILLISECONDS.toDays(timeLeft)
                    iconRes   = R.drawable.ic_signal
                    subtitle  = if (days <= 1) "Mañana" else "En ${days} días"
                    timeLabel = "${days}d"
                }
                else -> continue  // Más de 3 días: no aparece en alertas urgentes
            }
            alerts.add(AlertItem(iconRes = iconRes, title = event.title ?: "Evento",
                subtitle = subtitle, timeLabel = timeLabel, eventId = event.id,
                alertKey = "event_${event.id}"))
        }

        // Eventos lejanos (>3 días): aparecen en el marquee como info general
        for (event in currentEvents) {
            val date = event.date
            if (date != null && date.time > now && (date.time - now) > 3 * 24 * 60 * 60 * 1000L) {
                alerts.add(AlertItem(
                    iconRes   = R.drawable.ic_rocket,
                    title     = event.title ?: "Nuevo evento",
                    subtitle  = "${event.memberIds.size} crew · ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)}",
                    timeLabel = "",
                    eventId   = event.id,
                    alertKey  = "event_${event.id}"
                ))
            }
        }

        currentAlerts = alerts.filter { it.alertKey !in dismissedAlertKeys }
        updateMarquee()
    }

    // ── Marquee ───────────────────────────────────────────────────────────────

    private fun updateMarquee() {
        if (_binding == null) return

        if (currentAlerts.isEmpty()) {
            binding.tvMarquee.text        = "Órbita estable · Sin transmisiones pendientes"
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

        sheetBinding.tvNoAlerts.isVisible  = currentAlerts.isEmpty()
        sheetBinding.rvAlerts.isVisible    = currentAlerts.isNotEmpty()
        sheetBinding.btnClearAll.isVisible = currentAlerts.isNotEmpty()

        if (currentAlerts.isNotEmpty()) {
            val alertAdapter = AlertAdapter(
                showDismiss = true,
                onAlertClick = { alert ->
                    dialog.dismiss()
                    when {
                        alert.iconRes == R.drawable.ic_person_add -> {
                            activity?.findViewById<BottomNavigationView>(R.id.bottom_menu)
                                ?.selectedItemId = R.id.navigation_orbit
                            groupViewModel.signalOpenRequests()
                        }
                        alert.eventId != null -> {
                            val bundle = Bundle().apply { putString("eventId", alert.eventId) }
                            findNavController().navigate(
                                R.id.action_eventFragment_to_eventDetailFragment, bundle
                            )
                        }
                    }
                },
                onDismiss = { alert ->
                    if (alert.alertKey.isNotEmpty()) {
                        dismissedAlertKeys.add(alert.alertKey)
                        rebuildAlerts()
                        // Actualizar la lista en el sheet
                        sheetBinding.tvNoAlerts.isVisible  = currentAlerts.isEmpty()
                        sheetBinding.rvAlerts.isVisible    = currentAlerts.isNotEmpty()
                        sheetBinding.btnClearAll.isVisible  = currentAlerts.isNotEmpty()
                        (sheetBinding.rvAlerts.adapter as? AlertAdapter)?.submitList(currentAlerts)
                    }
                }
            )
            sheetBinding.rvAlerts.layoutManager = LinearLayoutManager(requireContext())
            sheetBinding.rvAlerts.adapter        = alertAdapter
            alertAdapter.submitList(currentAlerts)

            sheetBinding.btnClearAll.setOnClickListener {
                currentAlerts.forEach { dismissedAlertKeys.add(it.alertKey) }
                rebuildAlerts()
                sheetBinding.tvNoAlerts.isVisible  = true
                sheetBinding.rvAlerts.isVisible    = false
                sheetBinding.btnClearAll.isVisible  = false
                alertAdapter.submitList(emptyList())
            }
        }

        dialog.show()
    }

    companion object {
        // 24 horas en milisegundos. Umbral para considerar un evento "inminente"
        private const val IMMINENT_MS = 24 * 60 * 60 * 1000L
    }
}
