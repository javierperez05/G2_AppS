package com.example.cosmos.ui.Events

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.cosmos.Model.Actions.FriendRequest
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.databinding.FragmentEventBinding
import com.example.cosmos.databinding.BottomSheetAlertsBinding
import com.example.cosmos.ui.Events.rvEvents.EventAdapter
import com.example.cosmos.ui.Events.rvEvents.EventUiState
import com.example.cosmos.ui.Events.rvEvents.EventViewModel
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

    private val viewModel: EventViewModel by viewModels()

    private val eventAdapter = EventAdapter { event ->
        val bundle = Bundle().apply { putString("eventId", event.id ?: "") }
        findNavController().navigate(R.id.action_eventFragment_to_eventDetailFragment, bundle)
    }

    private val marqueeHandler = Handler(Looper.getMainLooper())
    private var marqueeRunnable: Runnable? = null
    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    private var currentAlerts: List<AlertItem> = emptyList()
    private var currentEvents: List<Event> = emptyList()
    private var currentRequests: List<FriendRequest> = emptyList()

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
        stopMarquee()
        stopCountdown()
        _binding = null
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private fun initUI() {
        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
        }
    }

    private fun initListeners() {
        binding.fabCreateEvent.setOnClickListener {
            findNavController().navigate(R.id.action_eventFragment_to_createEventFragment)
        }

        binding.marqueeContainer.setOnClickListener {
            showAlertsBottomSheet()
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
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state is EventUiState.Loading
                        binding.layoutEmpty.isVisible  = state is EventUiState.Empty
                        binding.rvEvents.isVisible     = state is EventUiState.Success

                        if (state is EventUiState.Success) {
                            currentEvents = state.events
                            val sorted = state.events.sortedBy { it.date?.time ?: Long.MAX_VALUE }
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

    // ── Proximo evento destacado ──────────────────────────────────────────────

    private fun setupNextEvent(sortedEvents: List<Event>) {
        val nextEvent = getNextEvent(sortedEvents)
        val hasNext = nextEvent != null

        binding.cardNextEvent.isVisible = hasNext
        binding.tvNextUpLabel.isVisible = hasNext

        if (nextEvent == null) return

        binding.tvNextEventTitle.text = nextEvent.title ?: "Sin titulo"
        binding.tvNextEventLocation.text = nextEvent.location ?: ""
        binding.tvNextEventLocation.isVisible = !nextEvent.location.isNullOrBlank()
        binding.tvNextEventCrew.text = "${nextEvent.memberIds.size} crew members"

        val eventDate = nextEvent.date
        if (eventDate != null) {
            val dateFormat = SimpleDateFormat("EEEE dd MMM · HH:mm", Locale.getDefault())
            binding.tvNextEventDate.text = dateFormat.format(eventDate)
            updateNextCountdown(eventDate)

            val timeLeft = eventDate.time - System.currentTimeMillis()
            val isImminent = timeLeft in 1..IMMINENT_MS
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
            binding.tvNextCountdown.text = "EN CURSO"
            binding.layoutNextCountdown.isVisible = true
            return
        }

        val days = TimeUnit.MILLISECONDS.toDays(timeLeft)
        val hours = TimeUnit.MILLISECONDS.toHours(timeLeft) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60

        binding.tvNextCountdown.text = when {
            days > 0 -> "T- ${days}d ${hours}h ${minutes}m"
            hours > 0 -> "T- ${hours}h ${minutes}m"
            else -> "T- ${minutes}m"
        }
        binding.layoutNextCountdown.isVisible = true
    }

    private fun getNextEvent(events: List<Event>): Event? {
        val now = System.currentTimeMillis()
        return events
            .filter { it.date != null && it.date.time > now }
            .minByOrNull { it.date!!.time }
    }

    // ── Countdown timer ───────────────────────────────────────────────────────

    private fun startCountdown() {
        stopCountdown()
        countdownRunnable = object : Runnable {
            override fun run() {
                if (_binding == null) return
                val nextEvent = getNextEvent(currentEvents)
                if (nextEvent?.date != null) {
                    updateNextCountdown(nextEvent.date)
                }
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

    private fun rebuildAlerts() {
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<AlertItem>()

        // Solicitudes de amistad pendientes
        if (currentRequests.isNotEmpty()) {
            if (currentRequests.size == 1) {
                val req = currentRequests.first()
                alerts.add(
                    AlertItem(
                        iconRes = R.drawable.ic_person_add,
                        title = "${req.fromUsername} quiere ser tu amigo",
                        subtitle = "Solicitud de amistad pendiente",
                        timeLabel = ""
                    )
                )
            } else {
                alerts.add(
                    AlertItem(
                        iconRes = R.drawable.ic_person_add,
                        title = "${currentRequests.size} solicitudes de amistad",
                        subtitle = currentRequests.take(3).joinToString(", ") { it.fromUsername },
                        timeLabel = ""
                    )
                )
            }
        }

        // Eventos proximos
        val sortedEvents = currentEvents
            .filter { it.date != null && it.date.time > now }
            .sortedBy { it.date!!.time }

        for (event in sortedEvents) {
            val date = event.date ?: continue
            val timeLeft = date.time - now

            val iconRes: Int
            val subtitle: String
            val timeLabel: String

            when {
                timeLeft <= IMMINENT_MS -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(timeLeft)
                    val mins = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60
                    iconRes = R.drawable.ic_alert_urgent
                    subtitle = "Lanzamiento inminente"
                    timeLabel = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                }
                timeLeft <= 3 * 24 * 60 * 60 * 1000L -> {
                    val days = TimeUnit.MILLISECONDS.toDays(timeLeft)
                    iconRes = R.drawable.ic_signal
                    subtitle = if (days <= 1) "Manana" else "En ${days} dias"
                    timeLabel = "${days}d"
                }
                else -> continue
            }

            alerts.add(
                AlertItem(
                    iconRes = iconRes,
                    title = event.title ?: "Evento",
                    subtitle = subtitle,
                    timeLabel = timeLabel,
                    eventId = event.id
                )
            )
        }

        // Eventos futuros lejanos (>3 dias)
        for (event in currentEvents) {
            val date = event.date
            if (date != null && date.time > now && (date.time - now) > 3 * 24 * 60 * 60 * 1000L) {
                alerts.add(
                    AlertItem(
                        iconRes = R.drawable.ic_rocket,
                        title = event.title ?: "Nuevo evento",
                        subtitle = "${event.memberIds.size} crew · ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)}",
                        timeLabel = "",
                        eventId = event.id
                    )
                )
            }
        }

        currentAlerts = alerts
        updateMarquee()
    }

    // ── Marquee ───────────────────────────────────────────────────────────────

    private fun updateMarquee() {
        if (_binding == null) return

        if (currentAlerts.isEmpty()) {
            binding.tvMarquee.text = "Orbita estable · Sin transmisiones pendientes"
            binding.tvMarqueeBadge.isVisible = false
            startAutoScroll()
            return
        }

        val marqueeText = currentAlerts.joinToString("     \u2022     ") { alert ->
            val time = if (alert.timeLabel.isNotEmpty()) " (${alert.timeLabel})" else ""
            "${alert.title} - ${alert.subtitle}$time"
        }
        binding.tvMarquee.text = "$marqueeText     \u2022     $marqueeText"

        binding.tvMarqueeBadge.isVisible = true
        binding.tvMarqueeBadge.text = "${currentAlerts.size}"

        startAutoScroll()
    }

    private fun startAutoScroll() {
        stopMarquee()
        val scrollView = binding.marqueeScroll
        scrollView.post {
            if (_binding == null) return@post
            val maxScroll = binding.tvMarquee.width - scrollView.width
            if (maxScroll <= 0) return@post

            marqueeRunnable = object : Runnable {
                var scrollX = 0
                override fun run() {
                    if (_binding == null) return
                    scrollX += 2
                    if (scrollX >= maxScroll / 2) scrollX = 0
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
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetAlertsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.tvNoAlerts.isVisible = currentAlerts.isEmpty()
        sheetBinding.rvAlerts.isVisible = currentAlerts.isNotEmpty()

        if (currentAlerts.isNotEmpty()) {
            val alertAdapter = AlertAdapter { alert ->
                dialog.dismiss()
                if (alert.eventId != null) {
                    val bundle = Bundle().apply { putString("eventId", alert.eventId) }
                    findNavController().navigate(R.id.action_eventFragment_to_eventDetailFragment, bundle)
                }
            }
            sheetBinding.rvAlerts.layoutManager = LinearLayoutManager(requireContext())
            sheetBinding.rvAlerts.adapter = alertAdapter
            alertAdapter.submitList(currentAlerts)
        }

        dialog.show()
    }

    companion object {
        private const val IMMINENT_MS = 24 * 60 * 60 * 1000L
    }
}
