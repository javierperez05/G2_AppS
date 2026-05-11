package com.example.cosmos.ui.Events.Detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Users.User
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemEventMemberBinding

class EventMemberAdapter : ListAdapter<User, EventMemberAdapter.MemberViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemEventMemberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MemberViewHolder(
        private val binding: ItemEventMemberBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvMemberUsername.text = user.username ?: "?"
            if (!user.profilePictureUrl.isNullOrBlank()) {
                Glide.with(binding.ivMemberAvatar)
                    .load(user.profilePictureUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_circle_profile)
                    .into(binding.ivMemberAvatar)
            } else {
                binding.ivMemberAvatar.setImageResource(R.drawable.ic_circle_profile)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(old: User, new: User) = old.id == new.id
        override fun areContentsTheSame(old: User, new: User) = old == new
    }
}
