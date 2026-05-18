package com.example.cosmos.ui.Orbit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Users.User
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemFriendBinding

class FriendAdapter(
    private val onAddClick: (User) -> Unit,
    private val onRemoveClick: (User) -> Unit,
    private val onAcceptClick: (User, String) -> Unit = { _, _ -> },  // user, requestId
    private val onRejectClick: (User, String) -> Unit = { _, _ -> }   // user, requestId
) : ListAdapter<User, FriendAdapter.FriendViewHolder>(FriendDiffCallback()) {

    var friendIds: Set<String> = emptySet()
        set(value) { field = value; notifyDataSetChanged() }

    var pendingSentIds: Set<String> = emptySet()
        set(value) { field = value; notifyDataSetChanged() }

    // Map of userId -> requestId for incoming requests
    var incomingRequestMap: Map<String, String> = emptyMap()
        set(value) { field = value; notifyDataSetChanged() }

    var isRequestMode: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FriendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FriendViewHolder(
        private val binding: ItemFriendBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvFriendUsername.text = user.username ?: binding.root.context.getString(R.string.no_name)
            binding.tvFriendEmail.text = user.email ?: ""

            // Avatar con Glide
            if (!user.profilePictureUrl.isNullOrEmpty()) {
                Glide.with(binding.root)
                    .load(user.profilePictureUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_circle_profile)
                    .into(binding.ivFriendAvatar)
            } else {
                binding.ivFriendAvatar.setImageResource(R.drawable.ic_circle_profile)
            }

            val userId = user.id ?: ""
            val isFriend = friendIds.contains(userId)
            val isPending = pendingSentIds.contains(userId)
            val requestId = incomingRequestMap[userId]

            if (isRequestMode && requestId != null) {
                // Incoming request mode: show accept/reject
                binding.btnFriendAction.isVisible = false
                binding.layoutRequestActions.isVisible = true
                binding.btnAccept.setOnClickListener { onAcceptClick(user, requestId) }
                binding.btnReject.setOnClickListener { onRejectClick(user, requestId) }
            } else {
                // Normal mode: show single action button
                binding.layoutRequestActions.isVisible = false
                binding.btnFriendAction.isVisible = true

                when {
                    isFriend -> {
                        binding.btnFriendAction.text = binding.root.context.getString(R.string.btn_friend)
                        binding.btnFriendAction.alpha = 0.5f
                        binding.btnFriendAction.setOnClickListener { onRemoveClick(user) }
                    }
                    isPending -> {
                        binding.btnFriendAction.text = binding.root.context.getString(R.string.btn_pending)
                        binding.btnFriendAction.alpha = 0.4f
                        binding.btnFriendAction.setOnClickListener { /* noop */ }
                    }
                    else -> {
                        binding.btnFriendAction.text = binding.root.context.getString(R.string.btn_send_request)
                        binding.btnFriendAction.alpha = 1f
                        binding.btnFriendAction.setOnClickListener { onAddClick(user) }
                    }
                }
            }
        }
    }

    private class FriendDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(old: User, new: User) = old.id == new.id
        override fun areContentsTheSame(old: User, new: User) = old == new
    }
}
