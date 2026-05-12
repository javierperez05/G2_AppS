package com.example.cosmos.ui.Events.rvEvents

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Users.User
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemSelectedMemberBinding

class SelectedMembersAdapter(
    private val members: MutableList<User>,
    private val onRemove: (User) -> Unit
) : RecyclerView.Adapter<SelectedMembersAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemSelectedMemberBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectedMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = members[position]
        holder.binding.tvMemberName.text = user.username ?: "?"
        if (!user.profilePictureUrl.isNullOrEmpty()) {
            Glide.with(holder.binding.ivMemberAvatar)
                .load(user.profilePictureUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_circle_profile)
                .into(holder.binding.ivMemberAvatar)
        } else {
            holder.binding.ivMemberAvatar.setImageResource(R.drawable.ic_circle_profile)
        }
        holder.binding.btnRemoveMember.setOnClickListener { onRemove(user) }
    }

    override fun getItemCount() = members.size
}
