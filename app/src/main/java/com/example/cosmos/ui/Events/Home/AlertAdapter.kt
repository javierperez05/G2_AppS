package com.example.cosmos.ui.Events.Home

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  AlertType — colores por categoría
 *      Cada tipo de alerta tiene un color de acento, tint del icono y
 *      color del punto en la cabecera de sección:
 *      - FRIEND_REQUEST: púrpura (#7C6DF0)
 *      - EVENT_INVITE: azul (#4A90D9)
 *      - IMMINENT: naranja (#FF6B3D) — eventos en <24h
 *      - UPCOMING: ámbar (#FFCC80) — eventos en 1-3 días
 *      - INFO: gris claro — eventos lejanos, informativos
 *
 *  AlertListItem — sealed class con dos tipos
 *      - Section: cabecera visual que separa grupos de alertas
 *      - Alert: un item de alerta real con datos y acción
 *      Esto permite que un solo RecyclerView muestre cabeceras y
 *      contenido mezclados usando dos viewTypes.
 *
 *  GradientDrawable.mutate()
 *      Los drawables XML se comparten entre todas las instancias.
 *      Si cambias el color de uno, cambias el de todos. mutate()
 *      crea una copia independiente para este ViewHolder concreto.
 * ═══════════════════════════════════════════════════════════════════
 */

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmos.databinding.ItemAlertSectionBinding
import com.example.cosmos.databinding.ItemNotificationAlertBinding

// ── Tipos de alerta (cada uno tiene colores propios) ───────────────────
enum class AlertType(val accentColor: Int, val iconTint: Int, val dotColor: Int) {
    FRIEND_REQUEST(Color.parseColor("#7C6DF0"), Color.parseColor("#B0A4F8"), Color.parseColor("#7C6DF0")),
    EVENT_INVITE(Color.parseColor("#4A90D9"),   Color.parseColor("#82B4E8"), Color.parseColor("#4A90D9")),
    IMMINENT(Color.parseColor("#FF6B3D"),       Color.parseColor("#FFAA88"), Color.parseColor("#FF6B3D")),
    UPCOMING(Color.parseColor("#FFCC80"),       Color.parseColor("#FFE0AA"), Color.parseColor("#FFCC80")),
    INFO(Color.parseColor("#55FFFFFF"),         Color.parseColor("#77FFFFFF"), Color.parseColor("#44FFFFFF"))
}

// ── Modelo de alerta ───────────────────────────────────────────────────
data class AlertItem(
    @DrawableRes val iconRes: Int,
    val title: String,
    val subtitle: String,
    val timeLabel: String,
    val eventId: String? = null,
    val alertKey: String = "",
    val type: AlertType = AlertType.INFO
)

// ── Items de la lista (alertas + cabeceras de sección) ─────────────────
sealed class AlertListItem {
    data class Section(val title: String, val count: Int, val dotColor: Int) : AlertListItem()
    data class Alert(val item: AlertItem) : AlertListItem()
}

// ── Adapter ────────────────────────────────────────────────────────────
class AlertAdapter(
    private val showDismiss: Boolean = false,
    private val onAlertClick: (AlertItem) -> Unit,
    private val onDismiss: ((AlertItem) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<AlertListItem>()

    fun submitList(list: List<AlertListItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    // ── ViewHolders ────────────────────────────────────────────────────

    inner class AlertViewHolder(val binding: ItemNotificationAlertBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SectionViewHolder(val binding: ItemAlertSectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    // ── ViewTypes ──────────────────────────────────────────────────────

    override fun getItemViewType(position: Int) = when (items[position]) {
        is AlertListItem.Section -> VIEW_TYPE_SECTION
        is AlertListItem.Alert   -> VIEW_TYPE_ALERT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SECTION -> SectionViewHolder(
                ItemAlertSectionBinding.inflate(inflater, parent, false)
            )
            else -> AlertViewHolder(
                ItemNotificationAlertBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val listItem = items[position]) {
            is AlertListItem.Section -> bindSection(holder as SectionViewHolder, listItem)
            is AlertListItem.Alert   -> bindAlert(holder as AlertViewHolder, listItem.item)
        }
    }

    override fun getItemCount() = items.size

    // ── Bind section header ────────────────────────────────────────────

    private fun bindSection(holder: SectionViewHolder, section: AlertListItem.Section) {
        with(holder.binding) {
            tvSectionTitle.text = section.title
            tvSectionCount.text = section.count.toString()
            // Tint del punto indicador con el color de la sección
            val dotBg = viewSectionDot.background
            if (dotBg is GradientDrawable) {
                dotBg.setColor(section.dotColor)
            } else {
                viewSectionDot.background.colorFilter =
                    PorterDuffColorFilter(section.dotColor, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    // ── Bind alert item ────────────────────────────────────────────────

    private fun bindAlert(holder: AlertViewHolder, item: AlertItem) {
        with(holder.binding) {
            // Icon
            ivAlertIcon.setImageResource(item.iconRes)
            ivAlertIcon.colorFilter = PorterDuffColorFilter(item.type.iconTint, PorterDuff.Mode.SRC_IN)

            // Icon circle bg tinted
            val iconBg = viewIconBg.background
            if (iconBg is GradientDrawable) {
                iconBg.mutate()
                iconBg.setColor(setAlpha(item.type.accentColor, 0.15f))
            }

            // Accent bar
            val accentBg = viewAccent.background
            if (accentBg is GradientDrawable) {
                accentBg.mutate()
                accentBg.setColor(item.type.accentColor)
            } else {
                viewAccent.setBackgroundColor(item.type.accentColor)
            }

            // Text
            tvAlertTitle.text = item.title
            tvAlertSubtitle.text = item.subtitle

            // Time chip
            if (item.timeLabel.isNotEmpty()) {
                tvAlertTime.isVisible = true
                tvAlertTime.text = item.timeLabel
                tvAlertTime.setTextColor(item.type.accentColor)
            } else {
                tvAlertTime.isVisible = false
            }

            // Click
            root.setOnClickListener { onAlertClick(item) }

            // Dismiss
            btnDismiss.isVisible = showDismiss && onDismiss != null
            btnDismiss.setOnClickListener { onDismiss?.invoke(item) }
        }
    }

    // Aplica alfa a un color (0.0 - 1.0)
    private fun setAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    companion object {
        private const val VIEW_TYPE_SECTION = 0
        private const val VIEW_TYPE_ALERT   = 1
    }
}
