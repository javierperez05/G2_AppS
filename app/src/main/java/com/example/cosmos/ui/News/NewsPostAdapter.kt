package com.example.cosmos.ui.News

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Dual layout: posts míos vs posts de otros
 *      getItemViewType() devuelve TYPE_MINE o TYPE_OTHER según si
 *      el userId del post coincide con currentUserId. Cada tipo
 *      infla un layout distinto (alineación izquierda vs derecha).
 *
 *  Datos resueltos externamente
 *      memberNames y avatarUrls se pasan al constructor (no se
 *      cargan aquí). NewsViewModel los resuelve una sola vez para
 *      todo el feed. El adapter solo los consulta por userId.
 *
 *  Time ago display
 *      formatTimeAgo() convierte el timestamp del post a texto
 *      relativo ("hace 5 min", "hace 2h", "hace 3d").
 *
 *  Acciones por post
 *      - onCrewClick: abre bottom sheet con las rates de la tripulación
 *      - onViewEvent: navega a EventDetail en readOnly
 *      - onProposeClick: abre bottom sheet para proponer a grupo/amigo
 *      - onCardClick: abre PostDetailBottomSheet con detalle completo
 * ═══════════════════════════════════════════════════════════════════
 */

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Actions.Post
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemNewsPostBinding

class NewsPostAdapter(
    private val currentUserId: String,
    private val memberNames: Map<String, String>,
    private val avatarUrls: Map<String, String> = emptyMap(),
    private val onCrewClick: (Post) -> Unit,
    private val onViewEvent: (String) -> Unit,
    private val onProposeClick: (Post) -> Unit = {},
    private val onCardClick: (Post) -> Unit = {}
) : ListAdapter<Post, NewsPostAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemNewsPostBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PostViewHolder(
        private val binding: ItemNewsPostBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            // Unificado: mostrar el mismo layout para posts propios y de otros
            // (usamos la sección "Other" como única representación visible).

            // ── Card click ────────────────────────────────────────────────────
            binding.root.setOnClickListener { onCardClick(post) }

            // ── Carousel ──────────────────────────────────────────────────────
            bindCarousel(post.imageUrls)

            // ── Stars helper ──────────────────────────────────────────────────
            val filled    = post.rating.toInt().coerceIn(0, 5)
            val starsText = "\u2605".repeat(filled) + "\u2606".repeat(5 - filled)
            val ratingText = "%.1f".format(post.rating)

            // ── Crew ──────────────────────────────────────────────────────────
            val crewText = post.memberIds.mapNotNull { memberNames[it] }.joinToString(" \u00B7 ")

            // ── Footer ────────────────────────────────────────────────────────
            binding.tvItemSummary.isVisible = !post.itemSummary.isNullOrBlank()
            binding.tvItemSummary.text = post.itemSummary ?: ""

            binding.tvEventTitle.text = post.eventTitle ?: ""
            binding.tvTimeAgo.text    = getTimeAgo(post.createdAt)

            // ── Avatar ────────────────────────────────────────────────────────
            val authorAvatar = avatarUrls[post.userId ?: ""]

            // Forzamos un único layout visible
            binding.layoutPostMine.isVisible  = false
            binding.layoutPostOther.isVisible = true

            loadAvatar(binding.ivAvatarOther, authorAvatar)
            binding.tvUsernameOther.text = "@${post.username ?: ""}"
            binding.tvStarsOther.text    = starsText
            binding.tvStarsOther.setTextColor(0xFFFFD700.toInt())
            binding.tvRatingOther.text   = ratingText

            binding.tvCommentOther.isVisible = !post.comment.isNullOrBlank()
            binding.tvCommentOther.text = post.comment ?: ""

            binding.tvCrewOther.isVisible = true
            binding.tvCrewOther.text = crewText
            binding.tvCrewOther.setOnClickListener { onCrewClick(post) }

            binding.btnViewEventOther.isVisible = true
            binding.btnProponerOther.isVisible  = true
            binding.btnViewEventOther.setOnClickListener { onViewEvent(post.eventId ?: "") }
            binding.btnProponerOther.setOnClickListener  { onProposeClick(post) }
        }

        // ── Carousel ──────────────────────────────────────────────────────────

        private fun bindCarousel(urls: List<String>) {
            if (urls.isEmpty()) {
                binding.frameCarousel.isVisible = false
                return
            }
            binding.frameCarousel.isVisible = true

            val rv = binding.rvPhotos
            val llDots = binding.llDots
            val tvCounter = binding.tvPhotoCounter

            // Attach snap helper only once
            if (rv.onFlingListener == null) {
                PagerSnapHelper().attachToRecyclerView(rv)
            }

            rv.layoutManager = LinearLayoutManager(
                rv.context, LinearLayoutManager.HORIZONTAL, false
            )
            rv.adapter = PostImageCarouselAdapter(urls)

            // Dots
            setupDots(llDots, urls.size)

            // Counter
            tvCounter.isVisible = urls.size > 1
            tvCounter.text = "1 / ${urls.size}"

            // Scroll listener — update dots + counter on page change
            val prevListener = rv.tag as? RecyclerView.OnScrollListener
            if (prevListener != null) rv.removeOnScrollListener(prevListener)

            val listener = object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val lm = rv.layoutManager as? LinearLayoutManager ?: return
                        val pos = lm.findFirstCompletelyVisibleItemPosition()
                            .takeIf { it >= 0 } ?: return
                        updateDots(llDots, pos)
                        tvCounter.text = "${pos + 1} / ${urls.size}"
                    }
                }
            }
            rv.tag = listener
            rv.addOnScrollListener(listener)
        }

        private fun setupDots(llDots: LinearLayout, count: Int) {
            llDots.removeAllViews()
            if (count <= 1) { llDots.isVisible = false; return }
            llDots.isVisible = true
            val ctx = llDots.context
            val dp7  = (7 * ctx.resources.displayMetrics.density).toInt()
            val dp4  = (4 * ctx.resources.displayMetrics.density).toInt()
            repeat(count) {
                val dot = View(ctx).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setSize(dp7, dp7)
                        setColor(0x55FFFFFF)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp7, dp7).also {
                        it.setMargins(dp4, 0, dp4, 0)
                    }
                }
                llDots.addView(dot)
            }
            updateDots(llDots, 0)
        }

        private fun updateDots(llDots: LinearLayout, position: Int) {
            repeat(llDots.childCount) { i ->
                val dot = llDots.getChildAt(i)
                (dot?.background as? GradientDrawable)?.setColor(
                    if (i == position) 0xFFFFFFFF.toInt() else 0x55FFFFFF
                )
            }
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private fun loadAvatar(iv: com.google.android.material.imageview.ShapeableImageView, urlOrBase64: String?) {
            if (urlOrBase64.isNullOrBlank()) {
                iv.setImageResource(R.drawable.ic_circle_profile)
                return
            }
            val trimmed = urlOrBase64.trim()
            // Si parece un URL, cargar directo
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                Glide.with(iv).load(trimmed).circleCrop()
                    .placeholder(R.drawable.ic_circle_profile)
                    .error(R.drawable.ic_circle_profile)
                    .into(iv)
                return
            }
            // Si no es URL, intentamos decodificar Base64
            try {
                val bytes = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
                Glide.with(iv).load(bytes).circleCrop()
                    .placeholder(R.drawable.ic_circle_profile)
                    .error(R.drawable.ic_circle_profile)
                    .into(iv)
            } catch (e: IllegalArgumentException) {
                // Base64 inválido -> fallback
                iv.setImageResource(R.drawable.ic_circle_profile)
            }
        }


        private fun getTimeAgo(timestamp: Long): String {
            val ctx     = binding.root.context
            val diff    = System.currentTimeMillis() - timestamp
            val minutes = diff / 60_000
            val hours   = minutes / 60
            val days    = hours / 24
            return when {
                days > 0    -> ctx.getString(R.string.time_days_ago, days.toInt())
                hours > 0   -> ctx.getString(R.string.time_hours_ago, hours.toInt())
                minutes > 0 -> ctx.getString(R.string.time_minutes_ago, minutes.toInt())
                else        -> ctx.getString(R.string.time_now)
            }
        }
    }

    private class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(old: Post, new: Post) = old.id == new.id
        override fun areContentsTheSame(old: Post, new: Post) = old == new
    }
}
