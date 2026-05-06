package com.example.cosmos.ui.News

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmos.Model.Actions.Rate
import com.example.cosmos.Model.Event.EventType
import com.example.cosmos.databinding.ItemNewsEventBinding
import java.text.SimpleDateFormat
import java.util.Locale

class NewsEventAdapter(
    private val userId: String,
    private val onRateSubmit: (eventId: String, rate: Rate) -> Unit
) : ListAdapter<NewsItem, NewsEventAdapter.NewsViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<NewsItem>() {
        override fun areItemsTheSame(a: NewsItem, b: NewsItem) = a.event.id == b.event.id
        override fun areContentsTheSame(a: NewsItem, b: NewsItem) = a == b
    }

    inner class NewsViewHolder(val binding: ItemNewsEventBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var selectedStars = 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        NewsViewHolder(
            ItemNewsEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

        // ── Cabecera ──────────────────────────────────────────────────────────
        b.tvNewsEmoji.text = if (item.event.type == EventType.SECRET) "\uD83D\uDD2E" else "\uD83D\uDE80"
        b.tvNewsEventTitle.text = item.event.title ?: ""
        b.tvNewsEventDate.text = item.event.date?.let { fmt.format(it) } ?: ""
        b.tvNewsEventLocation.text = item.event.location ?: ""
        b.tvNewsEventLocation.isVisible = !item.event.location.isNullOrBlank()

        // ── Resumen de valoraciones ───────────────────────────────────────────
        val rateCount = item.rates.size
        if (rateCount == 0) {
            b.tvAvgStars.text = "☆☆☆☆☆"
            b.tvAvgRating.text = "—"
            b.tvRateCount.text = "Sin valoraciones"
        } else {
            val avg = item.averageRating
            val filled = avg.toInt().coerceIn(0, 5)
            b.tvAvgStars.text = "★".repeat(filled) + "☆".repeat(5 - filled)
            b.tvAvgRating.text = "%.1f".format(avg)
            b.tvRateCount.text = "($rateCount valoracion${if (rateCount == 1) "" else "es"})"
        }

        // ── Preview de comentarios (hasta 2) ─────────────────────────────────
        b.llComments.removeAllViews()
        item.rates
            .filter { !it.comment.isNullOrBlank() }
            .take(2)
            .forEach { rate ->
                val tv = TextView(b.root.context).apply {
                    text = "\u201C${rate.comment}\u201D"
                    setTextColor(Color.parseColor("#88FFFFFF"))
                    textSize = 12f
                    setPadding(0, 0, 0, 6)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                b.llComments.addView(tv)
            }

        // ── Seccion de valoracion ─────────────────────────────────────────────
        val alreadyRated = item.userRate != null
        b.layoutRateSection.isVisible = !alreadyRated
        b.layoutMyRate.isVisible = alreadyRated

        if (alreadyRated) {
            val myRate = item.userRate!!
            val filled = myRate.rating.toInt().coerceIn(0, 5)
            b.tvMyRating.text = "★".repeat(filled) + "☆".repeat(5 - filled)
            b.tvMyComment.text = myRate.comment ?: ""
            b.tvMyComment.isVisible = !myRate.comment.isNullOrBlank()
        } else {
            // Resetear selector al hacer rebind
            holder.selectedStars = 0
            updateStarSelector(b, holder.selectedStars)

            val starViews = (0 until 5).map { b.llStarSelector.getChildAt(it) as TextView }
            starViews.forEachIndexed { i, tv ->
                tv.setOnClickListener {
                    holder.selectedStars = i + 1
                    updateStarSelector(b, holder.selectedStars)
                }
            }

            b.btnSubmitRate.setOnClickListener {
                if (holder.selectedStars == 0) return@setOnClickListener
                val comment = b.etRateComment.text?.toString()?.trim()
                val rate = Rate(
                    userId  = userId,
                    eventId = item.event.id,
                    rating  = holder.selectedStars.toFloat(),
                    comment = comment?.ifBlank { null }
                )
                onRateSubmit(item.event.id ?: return@setOnClickListener, rate)
            }
        }
    }

    private fun updateStarSelector(b: ItemNewsEventBinding, count: Int) {
        for (i in 0 until 5) {
            val tv = b.llStarSelector.getChildAt(i) as TextView
            if (i < count) {
                tv.text = "★"
                tv.setTextColor(Color.parseColor("#FFD700"))
            } else {
                tv.text = "☆"
                tv.setTextColor(Color.parseColor("#44FFFFFF"))
            }
        }
    }
}
