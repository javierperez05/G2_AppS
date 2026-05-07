package com.example.cosmos.ui.Events

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmos.databinding.ItemNotificationAlertBinding

data class AlertItem(
    @DrawableRes val iconRes: Int,
    val title: String,
    val subtitle: String,
    val timeLabel: String,
    val eventId: String? = null
)

class AlertAdapter(
    private val onAlertClick: (AlertItem) -> Unit
) : RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

    private val items = mutableListOf<AlertItem>()

    fun submitList(list: List<AlertItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class AlertViewHolder(val binding: ItemNotificationAlertBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemNotificationAlertBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            ivAlertIcon.setImageResource(item.iconRes)
            tvAlertTitle.text = item.title
            tvAlertSubtitle.text = item.subtitle
            tvAlertTime.text = item.timeLabel
            root.setOnClickListener { onAlertClick(item) }
        }
    }

    override fun getItemCount() = items.size
}
