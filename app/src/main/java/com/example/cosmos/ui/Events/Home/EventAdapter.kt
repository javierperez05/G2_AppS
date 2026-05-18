package com.example.cosmos.ui.Events.Home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.EventType
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemEventCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class EventListItem(
    val event: Event,
    val isPending: Boolean = false,
    val avatarUrls: Map<String, String> = emptyMap(),
    val inviterName: String? = null
)

class EventAdapter(
    private val onEventClick: (Event) -> Unit,
    private val onAcceptInvite: ((Event) -> Unit)? = null,
    private val onRejectInvite: ((Event) -> Unit)? = null
) : ListAdapter<EventListItem, EventAdapter.EventViewHolder>(EventDiffCallback()) {

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
        val item = getItem(position)
        val event = item.event
        val now = System.currentTimeMillis()

        with(holder.binding) {
            // Textos basicos
            tvEventTitle.text = event.title ?: "Sin titulo"
            ivEventIcon.setImageResource(
                if (event.type == EventType.SECRET) R.drawable.ic_secret else R.drawable.ic_rocket
            )
            tvEventLocation.text = event.location ?: ""
            tvEventLocation.isVisible = !event.location.isNullOrBlank()
            tvMemberCount.text = "${event.memberIds.size} crew"

            // Avatares de miembros (max 3)
            val avatarViews = listOf(ivAvatar1, ivAvatar2, ivAvatar3)
            val memberAvatars = event.memberIds.mapNotNull { id ->
                item.avatarUrls[id]?.ifEmpty { null }?.let { id to it }
            }.take(3)
            avatarViews.forEach { it.isVisible = false }
            memberAvatars.forEachIndexed { i, (_, url) ->
                avatarViews[i].isVisible = true
                Glide.with(avatarViews[i])
                    .load(url)
                    .circleCrop()
                    .placeholder(R.drawable.ic_circle_profile)
                    .into(avatarViews[i])
            }

            // Banner de invitación pendiente
            layoutPending.isVisible = item.isPending
            if (item.isPending) {
                viewAccent.setBackgroundColor(Color.parseColor("#7C6DF0"))
                root.alpha = 1f
                tvPendingLabel.text = if (!item.inviterName.isNullOrBlank())
                    "INVITADO POR @${item.inviterName}" else "INVITACION PENDIENTE"
                btnAcceptInvite.setOnClickListener { onAcceptInvite?.invoke(event) }
                btnRejectInvite.setOnClickListener { onRejectInvite?.invoke(event) }
            }

            // Fecha
            val eventDate = event.date
            if (eventDate != null) {
                tvEventDate.text = DATE_FORMAT.format(eventDate)
                tvEventDate.isVisible = true

                // Logica inminente
                val timeLeft = eventDate.time - now
                val isImminent = timeLeft in 1..IMMINENT_THRESHOLD_MS
                val isPast = timeLeft <= 0

                layoutImminent.isVisible = isImminent && !item.isPending
                if (isImminent && !item.isPending) {
                    tvCountdown.text = formatCountdown(timeLeft)
                    viewAccent.setBackgroundColor(Color.parseColor("#FF6B3D"))
                    startPulse(dotPulse)
                } else if (!item.isPending) {
                    viewAccent.setBackgroundColor(Color.parseColor("#1717AB"))
                    dotPulse.clearAnimation()
                }

                // Eventos pasados: estilo tenue
                if (isPast && !item.isPending) {
                    tvEventDate.text = root.context.getString(R.string.finished)
                    tvEventDate.setTextColor(Color.parseColor("#44FFFFFF"))
                    root.alpha = 0.6f
                } else if (!item.isPending) {
                    root.alpha = 1f
                    tvEventDate.setTextColor(
                        if (isImminent) Color.parseColor("#FFCC80") else Color.parseColor("#66FFFFFF")
                    )
                }
            } else {
                tvEventDate.text = root.context.getString(R.string.date_tbc)
                tvEventDate.isVisible = true
                layoutImminent.isVisible = false
                if (!item.isPending) root.alpha = 1f
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

    private class EventDiffCallback : DiffUtil.ItemCallback<EventListItem>() {
        override fun areItemsTheSame(old: EventListItem, new: EventListItem) = old.event.id == new.event.id
        override fun areContentsTheSame(old: EventListItem, new: EventListItem) = old == new
    }
}
