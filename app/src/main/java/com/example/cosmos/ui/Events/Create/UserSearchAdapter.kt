package com.example.cosmos.ui.Events.Create

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Users.User
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemUserSearchBinding

class UserSearchAdapter(
    private var users: List<User>,
    private val onUserClick: (User) -> Unit
) : RecyclerView.Adapter<UserSearchAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemUserSearchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.binding.tvSearchUsername.text = user.username ?: "?"
        holder.binding.tvSearchEmail.text = user.email ?: ""
        if (!user.profilePictureUrl.isNullOrEmpty()) {
            Glide.with(holder.binding.ivSearchAvatar)
                .load(user.profilePictureUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_circle_profile)
                .into(holder.binding.ivSearchAvatar)
        } else {
            holder.binding.ivSearchAvatar.setImageResource(R.drawable.ic_circle_profile)
        }
        holder.itemView.setOnClickListener { onUserClick(user) }
    }

    override fun getItemCount() = users.size

    fun updateList(newList: List<User>) {
        users = newList
        notifyDataSetChanged()
    }
}
