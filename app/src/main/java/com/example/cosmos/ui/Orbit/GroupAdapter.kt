package com.example.cosmos.ui.Orbit

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Users.Group
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemGroupOrbitalBinding

class GroupAdapter(
    private val onGroupClick: (Group) -> Unit
) : ListAdapter<Group, GroupAdapter.GroupViewHolder>(GroupDiffCallback()) {

    private val accentColors = listOf(
        "#7C6DF0", "#0EA5E9", "#34D399",
        "#F59E0B", "#F472B6", "#A78BFA"
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupOrbitalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class GroupViewHolder(
        private val binding: ItemGroupOrbitalBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: Group, position: Int) {
            val accentHex = accentColors[position % accentColors.size]

            // Nullables manejados con ?: porque name y description son String?
            binding.tvGroupName.text   = group.name ?: "Órbita"
            binding.tvGroupMeta.text   = group.description ?: "Sin descripción"
            binding.tvMemberCount.text = "${group.memberIds.size} ✦"
            binding.viewAccent.setBackgroundColor(Color.parseColor(accentHex))

            if (!group.imageUrl.isNullOrBlank()) {
                Glide.with(binding.ivGroupImage)
                    .load(group.imageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_circle_profile)
                    .into(binding.ivGroupImage)
            } else {
                binding.ivGroupImage.setImageResource(R.drawable.ic_circle_profile)
            }

            binding.cardGroup.setOnClickListener { onGroupClick(group) }
        }
    }

    private class GroupDiffCallback : DiffUtil.ItemCallback<Group>() {
        override fun areItemsTheSame(old: Group, new: Group) = old.id == new.id
        override fun areContentsTheSame(old: Group, new: Group) = old == new
    }
}