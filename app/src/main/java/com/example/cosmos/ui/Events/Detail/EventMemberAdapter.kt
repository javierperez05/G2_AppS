package com.example.cosmos.ui.Events.Detail

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Lista horizontal de avatares de tripulación
 *      Se muestra debajo del título del evento. Cada item es un
 *      avatar circular cargado con Glide. ListAdapter con DiffUtil
 *      para animaciones suaves al cambiar la lista de miembros.
 * ═══════════════════════════════════════════════════════════════════
 */

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
            Glide.with(binding.ivMemberAvatar)
                .load(user.profilePictureUrl.takeUnless { it.isNullOrBlank() })
                .circleCrop()
                .placeholder(R.drawable.ic_circle_profile)
                .error(R.drawable.ic_circle_profile)
                .into(binding.ivMemberAvatar)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(old: User, new: User) = old.id == new.id
        override fun areContentsTheSame(old: User, new: User) = old == new
    }
}
