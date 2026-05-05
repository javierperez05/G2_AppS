package com.example.cosmos.ui.Orbit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmos.Model.Users.User
import com.example.cosmos.databinding.ItemFriendBinding

class FriendAdapter(
    private val onActionClick: (User, Boolean) -> Unit  // (user, isFriend)
) : ListAdapter<User, FriendAdapter.FriendViewHolder>(FriendDiffCallback()) {

    // Set de IDs de amigos actuales para saber qué botón mostrar
    var friendIds: Set<String> = emptySet()
        set(value) { field = value; notifyDataSetChanged() }

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
            binding.tvFriendUsername.text = user.username ?: "Sin nombre"
            binding.tvFriendEmail.text    = user.email ?: ""

            val isFriend = friendIds.contains(user.id)
            if (isFriend) {
                binding.btnFriendAction.text      = "✓ Amigo"
                binding.btnFriendAction.alpha     = 0.5f
            } else {
                binding.btnFriendAction.text      = "+ Añadir"
                binding.btnFriendAction.alpha     = 1f
            }

            binding.btnFriendAction.setOnClickListener {
                onActionClick(user, isFriend)
            }
        }
    }

    private class FriendDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(old: User, new: User) = old.id == new.id
        override fun areContentsTheSame(old: User, new: User) = old == new
    }
}
