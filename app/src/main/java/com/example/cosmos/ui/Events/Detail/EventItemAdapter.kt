package com.example.cosmos.ui.Events.Detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmos.Model.Event.EventItem
import com.example.cosmos.databinding.ItemEventItemBinding

class EventItemAdapter(
    private val currentUserId: String,
    private var memberNames: Map<String, String>,
    private val onDelete: (EventItem) -> Unit
) : ListAdapter<EventItem, EventItemAdapter.ItemViewHolder>(DiffCallback()) {

    fun updateMemberNames(names: Map<String, String>) {
        memberNames = names
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemEventItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ItemViewHolder(
        private val binding: ItemEventItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EventItem) {
            binding.tvItemName.text = item.name

            binding.tvItemPrice.text = if (item.price > 0) "%.2f€".format(item.price) else ""

            val payerName = memberNames[item.paidByUserId] ?: "?"
            binding.tvItemPayer.text = "Paga: $payerName"

            val splitCount = item.splitBetweenUserIds.size
            binding.tvItemSplit.text = if (splitCount > 0) "÷ $splitCount" else ""

            // Solo puede borrar quien lo creó (el que paga) o cualquier miembro
            binding.btnDeleteItem.setOnClickListener { onDelete(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<EventItem>() {
        override fun areItemsTheSame(old: EventItem, new: EventItem) = old.id == new.id
        override fun areContentsTheSame(old: EventItem, new: EventItem) = old == new
    }
}
