package com.example.cosmos.ui.News

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.R
import com.example.cosmos.databinding.BottomsheetPostDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PostDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetPostDetailBinding? = null
    private val binding get() = _binding!!

    private val newsViewModel: NewsViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val post = newsViewModel.selectedPost.value ?: run { dismiss(); return }
        val uiState = newsViewModel.uiState.value
        val memberNames = if (uiState is NewsUiState.Success) uiState.memberNames else emptyMap()
        val avatarUrls  = if (uiState is NewsUiState.Success) uiState.avatarUrls  else emptyMap()

        // ── Carousel ──────────────────────────────────────────────────────────
        bindCarousel(post.imageUrls)

        // ── Header ────────────────────────────────────────────────────────────
        val avatarUrl = avatarUrls[post.userId ?: ""]
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(binding.ivDetailAvatar).load(avatarUrl).circleCrop()
                .placeholder(R.drawable.ic_circle_profile).into(binding.ivDetailAvatar)
        } else {
            binding.ivDetailAvatar.setImageResource(R.drawable.ic_circle_profile)
        }

        binding.tvDetailUsername.text = "@${post.username ?: ""}"
        binding.tvDetailTimeAgo.text  = getTimeAgo(post.createdAt)

        val filled = post.rating.toInt().coerceIn(0, 5)
        binding.tvDetailStars.text = "\u2605".repeat(filled) + "\u2606".repeat(5 - filled)
        binding.tvDetailStars.setTextColor(0xFFFFD700.toInt())
        binding.tvDetailRating.text = "%.1f".format(post.rating)

        // ── Comment ───────────────────────────────────────────────────────────
        binding.tvDetailComment.isVisible = !post.comment.isNullOrBlank()
        binding.tvDetailComment.text = post.comment ?: ""

        // ── Crew ──────────────────────────────────────────────────────────────
        val crewText = post.memberIds.mapNotNull { memberNames[it] }.joinToString(" \u00B7 ")
        binding.tvDetailCrew.text = crewText
        binding.tvDetailCrew.setOnClickListener {
            dismiss()
            newsViewModel.loadCrewRates(post.eventId ?: "", post.memberIds)
        }

        // ── Footer ────────────────────────────────────────────────────────────
        binding.tvDetailEventTitle.text = post.eventTitle ?: ""

        // ── Buttons ───────────────────────────────────────────────────────────
        binding.btnDetailViewEvent.setOnClickListener {
            dismiss()
            (parentFragment as? NewsFragment)?.navigateToEvent(post.eventId ?: "")
        }
        binding.btnDetailPropose.setOnClickListener {
            dismiss()
            newsViewModel.loadProposeData(
                (parentFragment as? NewsFragment)?.currentUserId ?: "", post
            )
        }
    }

    // ── Carousel (same logic as NewsPostAdapter) ───────────────────────────────

    private fun bindCarousel(urls: List<String>) {
        if (urls.isEmpty()) {
            binding.frameDetailCarousel.isVisible = false
            return
        }
        binding.frameDetailCarousel.isVisible = true

        val rv = binding.rvDetailPhotos
        val llDots = binding.llDetailDots
        val tvCounter = binding.tvDetailPhotoCounter

        if (rv.onFlingListener == null) {
            PagerSnapHelper().attachToRecyclerView(rv)
        }
        rv.layoutManager = LinearLayoutManager(rv.context, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = PostImageCarouselAdapter(urls)

        setupDots(llDots, urls.size)
        tvCounter.isVisible = urls.size > 1
        tvCounter.text = "1 / ${urls.size}"

        val prevListener = rv.tag as? RecyclerView.OnScrollListener
        if (prevListener != null) rv.removeOnScrollListener(prevListener)

        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = rv.layoutManager as? LinearLayoutManager ?: return
                    val pos = lm.findFirstCompletelyVisibleItemPosition().takeIf { it >= 0 } ?: return
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
        val dp7 = (7 * ctx.resources.displayMetrics.density).toInt()
        val dp4 = (4 * ctx.resources.displayMetrics.density).toInt()
        repeat(count) {
            val dot = View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setSize(dp7, dp7)
                    setColor(0x55FFFFFF)
                }
                layoutParams = LinearLayout.LayoutParams(dp7, dp7).also { it.setMargins(dp4, 0, dp4, 0) }
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

    private fun getTimeAgo(timestamp: Long): String {
        val diff    = System.currentTimeMillis() - timestamp
        val minutes = diff / 60_000
        val hours   = minutes / 60
        val days    = hours / 24
        return when {
            days > 0    -> "hace ${days}d"
            hours > 0   -> "hace ${hours}h"
            minutes > 0 -> "hace ${minutes}min"
            else        -> "ahora"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
