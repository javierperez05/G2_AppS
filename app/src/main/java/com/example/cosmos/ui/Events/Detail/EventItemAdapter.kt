package com.example.cosmos.ui.Events.Detail

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  EventItem — gastos compartidos (estilo Tricount)
 *      Cada item tiene: nombre, precio, quién pagó (paidByUserId)
 *      y entre quiénes se reparte (splitBetweenUserIds).
 *      El adapter muestra la info + botón eliminar (solo para
 *      el pagador y si no es readOnly).
 *
 *  memberNames (Map<String, String>)
 *      Se actualiza externamente para resolver IDs a nombres.
 *      Se usa para mostrar "Pagado por @nombre" y "Reparto: a, b, c".
 * ═══════════════════════════════════════════════════════════════════
 */

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmos.Model.Event.EventItem
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemEventItemBinding

class EventItemAdapter(
    private val currentUserId: String,
    private var memberNames: Map<String, String>,
    private val onDelete: (EventItem) -> Unit,
    var readOnly: Boolean = false
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

            if (readOnly) {
                // Solo nombre + precio, sin info de quién paga
                binding.tvItemPayer.isVisible = false
                binding.tvItemSplit.isVisible = false
                binding.btnDeleteItem.isVisible = false
            } else {
                binding.tvItemPayer.isVisible = true
                binding.tvItemSplit.isVisible = true
                binding.btnDeleteItem.isVisible = true

                val payerName = memberNames[item.paidByUserId] ?: "?"
                binding.tvItemPayer.text = binding.root.context.getString(R.string.pays_format, payerName)

                val splitCount = item.splitBetweenUserIds.size
                binding.tvItemSplit.text = if (splitCount > 0) "÷ $splitCount" else ""

                binding.btnDeleteItem.setOnClickListener { onDelete(item) }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<EventItem>() {
        override fun areItemsTheSame(old: EventItem, new: EventItem) = old.id == new.id
        override fun areContentsTheSame(old: EventItem, new: EventItem) = old == new
    }
}
