package com.example.cosmos.ui.Events.rvEvents

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.databinding.ItemEventBinding

class EventAdapter(private var eventList: List<Event> = emptyList()) :
    RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    inner class EventViewHolder(val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = eventList[position]
        with(holder.binding) {
            tvEventTitle.text = event.title
            tvEventDescription.text = event.description
            // Aquí usarías Glide o Coil para la imagen: ivEvent.load(event.imageUrl)
        }
    }

    override fun getItemCount(): Int = eventList.size

    fun updateData(newList: List<Event>) {
        this.eventList = newList
        notifyDataSetChanged() // Idealmente usar DiffUtil para mejor rendimiento
    }


}