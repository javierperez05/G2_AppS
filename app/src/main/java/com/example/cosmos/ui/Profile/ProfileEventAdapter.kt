package com.example.cosmos.ui.Profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.EventType
import com.example.cosmos.databinding.ItemEventGridBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileEventAdapter(
    private val onEventClick: (Event) -> Unit
) : ListAdapter<Event, ProfileEventAdapter.GridViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(a: Event, b: Event) = a.id == b.id
        override fun areContentsTheSame(a: Event, b: Event) = a == b
    }

    inner class GridViewHolder(val binding: ItemEventGridBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        GridViewHolder(
            ItemEventGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        val event = getItem(position)
        val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
        with(holder.binding) {
            tvGridEventEmoji.text = if (event.type == EventType.SECRET) "\uD83D\uDD2E" else "\uD83D\uDE80"
            tvGridEventTitle.text = event.title ?: ""
            tvGridEventDate.text = event.date?.let { fmt.format(it) } ?: ""
            root.setOnClickListener { onEventClick(event) }
        }
    }
}
