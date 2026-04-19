package com.example.cosmos.ui.Events.rvEvents

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmos.Model.Users.User

class SelectedMembersAdapter(
    private val members: MutableList<User>,
    private val onRemove: (User) -> Unit
) : RecyclerView.Adapter<SelectedMembersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(android.R.id.text1) // Usamos un layout simple de Android para ir rápido
        val root: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = members[position]
        holder.name.text = user.username
        holder.name.setTextColor(Color.WHITE)
        holder.root.setOnClickListener { onRemove(user) }
    }

    override fun getItemCount() = members.size
}