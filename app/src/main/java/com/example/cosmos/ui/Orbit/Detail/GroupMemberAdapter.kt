package com.example.cosmos.ui.Orbit.Detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemGroupMemberBinding
import com.example.cosmos.Model.Users.User

data class GroupMemberItem(
    val user: User,
    val isAdmin: Boolean,
    val isInvited: Boolean,
    val isSelf: Boolean
)

class GroupMemberAdapter(
    private val currentUserId: String,
    private val isAdmin: Boolean,
    private val onKick: (User) -> Unit
) : ListAdapter<GroupMemberItem, GroupMemberAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupMemberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemGroupMemberBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GroupMemberItem) {
            binding.tvMemberUsername.text = item.user.username ?: "?"

            if (!item.user.profilePictureUrl.isNullOrEmpty()) {
                Glide.with(binding.ivMemberAvatar)
                    .load(item.user.profilePictureUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_circle_profile)
                    .into(binding.ivMemberAvatar)
            } else {
                binding.ivMemberAvatar.setImageResource(R.drawable.ic_circle_profile)
            }

            when {
                item.isInvited -> {
                    binding.tvMemberBadge.isVisible = true
                    binding.tvMemberBadge.text = binding.root.context.getString(R.string.badge_invited)
                    binding.tvMemberBadge.setTextColor(0x88FFFFFF.toInt())
                }
                item.isAdmin -> {
                    binding.tvMemberBadge.isVisible = true
                    binding.tvMemberBadge.text = binding.root.context.getString(R.string.badge_admin)
                    binding.tvMemberBadge.setTextColor(0xFFC4BCFF.toInt())
                }
                else -> {
                    binding.tvMemberBadge.isVisible = false
                }
            }

            // El botón de expulsar solo lo ve el admin, sobre no-admins, y no sobre sí mismo
            val canKick = isAdmin && !item.isSelf && !item.isAdmin && !item.isInvited
            binding.btnKick.isVisible = canKick
            if (canKick) {
                binding.btnKick.setOnClickListener { onKick(item.user) }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<GroupMemberItem>() {
        override fun areItemsTheSame(old: GroupMemberItem, new: GroupMemberItem) =
            old.user.id == new.user.id && old.isInvited == new.isInvited
        override fun areContentsTheSame(old: GroupMemberItem, new: GroupMemberItem) = old == new
    }
}
