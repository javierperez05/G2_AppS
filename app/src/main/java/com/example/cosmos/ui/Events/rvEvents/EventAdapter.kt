package com.example.cosmos.ui.Events.rvEvents

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.EventType
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemEventCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class EventAdapter(
    private val onEventClick: (Event) -> Unit
) : ListAdapter<Event, EventAdapter.EventViewHolder>(EventDiffCallback()) {

    companion object {
        private const val IMMINENT_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24h
        private val DATE_FORMAT = SimpleDateFormat("EEE dd MMM · HH:mm", Locale.getDefault())
    }

    inner class EventViewHolder(val binding: ItemEventCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = getItem(position)
        val now = System.currentTimeMillis()

        with(holder.binding) {
            // Textos basicos
            tvEventTitle.text = event.title ?: "Sin titulo"
            ivEventIcon.setImageResource(
                if (event.type == EventType.SECRET) R.drawable.ic_secret else R.drawable.ic_rocket
            )
            tvEventLocation.text = event.location ?: ""
            tvEventLocation.isVisible = !event.location.isNullOrBlank()
            tvMemberCount.text = "${event.memberIds.size}"

            // Fecha
            val eventDate = event.date
            if (eventDate != null) {
                tvEventDate.text = DATE_FORMAT.format(eventDate)
                tvEventDate.isVisible = true

                // Logica inminente
                val timeLeft = eventDate.time - now
                val isImminent = timeLeft in 1..IMMINENT_THRESHOLD_MS
                val isPast = timeLeft <= 0

                layoutImminent.isVisible = isImminent
                if (isImminent) {
                    tvCountdown.text = formatCountdown(timeLeft)
                    // Acento naranja para inminente
                    viewAccent.setBackgroundColor(Color.parseColor("#FF6B3D"))
                    // Pulso en el dot
                    startPulse(dotPulse)
                } else {
                    viewAccent.setBackgroundColor(Color.parseColor("#1717AB"))
                    dotPulse.clearAnimation()
                }

                // Eventos pasados: estilo tenue
                if (isPast) {
                    tvEventDate.text = "Finalizado"
                    tvEventDate.setTextColor(Color.parseColor("#44FFFFFF"))
                    root.alpha = 0.6f
                } else {
                    root.alpha = 1f
                    tvEventDate.setTextColor(
                        if (isImminent) Color.parseColor("#FFCC80") else Color.parseColor("#66FFFFFF")
                    )
                }
            } else {
                tvEventDate.text = "Fecha por confirmar"
                tvEventDate.isVisible = true
                layoutImminent.isVisible = false
                root.alpha = 1f
            }

            root.setOnClickListener { onEventClick(event) }
        }
    }

    private fun formatCountdown(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return when {
            hours > 0 -> "T-${hours}h ${minutes}m"
            else -> "T-${minutes}m"
        }
    }

    private fun startPulse(view: View) {
        view.clearAnimation()
        ObjectAnimator.ofFloat(view, "alpha", 1f, 0.2f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(old: Event, new: Event) = old.id == new.id
        override fun areContentsTheSame(old: Event, new: Event) = old == new
    }
}
