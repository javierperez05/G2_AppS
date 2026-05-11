package com.example.cosmos.ui.News

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Actions.Post
import com.example.cosmos.databinding.ItemNewsPostBinding

class NewsPostAdapter(
    private val currentUserId: String,
    private val memberNames: Map<String, String>,
    private val onCrewClick: (Post) -> Unit,
    private val onViewEvent: (String) -> Unit
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
            val isMine = post.userId == currentUserId

            binding.layoutPostMine.isVisible = isMine
            binding.layoutPostOther.isVisible = !isMine

            // Stars helper
            val filled = post.rating.toInt().coerceIn(0, 5)
            val starsText = "\u2605".repeat(filled) + "\u2606".repeat(5 - filled)
            val ratingText = "%.1f".format(post.rating)

            // Crew names
            val crewText = post.memberIds.mapNotNull { memberNames[it] }.joinToString(" \u00B7 ")

            // Footer
            binding.tvEventTitle.text = post.eventTitle ?: ""
            binding.tvTimeAgo.text = getTimeAgo(post.createdAt)

            val firstImage = post.imageUrls.firstOrNull()

            if (isMine) {
                binding.ivPhotoMine.isVisible = firstImage != null
                if (firstImage != null) {
                    Glide.with(binding.ivPhotoMine).load(firstImage).centerCrop().into(binding.ivPhotoMine)
                }
                binding.tvUsernameMine.text = "@${post.username ?: ""}"
                binding.tvStarsMine.text = starsText
                binding.tvStarsMine.setTextColor(0xFFFFD700.toInt())
                binding.tvRatingMine.text = ratingText
                binding.tvCommentMine.text = post.comment ?: ""
                binding.tvCommentMine.isVisible = !post.comment.isNullOrBlank()
                binding.tvCrewMine.text = crewText
                binding.tvCrewMine.setOnClickListener { onCrewClick(post) }
                binding.btnViewEventMine.setOnClickListener { onViewEvent(post.eventId ?: "") }
            } else {
                binding.ivPhotoOther.isVisible = firstImage != null
                if (firstImage != null) {
                    Glide.with(binding.ivPhotoOther).load(firstImage).centerCrop().into(binding.ivPhotoOther)
                }
                binding.tvUsernameOther.text = "@${post.username ?: ""}"
                binding.tvStarsOther.text = starsText
                binding.tvStarsOther.setTextColor(0xFFFFD700.toInt())
                binding.tvRatingOther.text = ratingText
                binding.tvCommentOther.text = post.comment ?: ""
                binding.tvCommentOther.isVisible = !post.comment.isNullOrBlank()
                binding.tvCrewOther.text = crewText
                binding.tvCrewOther.setOnClickListener { onCrewClick(post) }
                binding.btnViewEventOther.setOnClickListener { onViewEvent(post.eventId ?: "") }
            }
        }

        private fun getTimeAgo(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            val minutes = diff / 60_000
            val hours = minutes / 60
            val days = hours / 24
            return when {
                days > 0 -> "hace ${days}d"
                hours > 0 -> "hace ${hours}h"
                minutes > 0 -> "hace ${minutes}min"
                else -> "ahora"
            }
        }
    }

    private class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(old: Post, new: Post) = old.id == new.id
        override fun areContentsTheSame(old: Post, new: Post) = old == new
    }
}
