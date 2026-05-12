package com.example.cosmos.ui.News

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Actions.Rate
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemCrewRateBinding

data class CrewRateItem(
    val userId: String,
    val username: String,
    val rate: Rate?,
    val avatarUrl: String? = null
)

class CrewRateAdapter(
    private val items: List<CrewRateItem>,
    private val onClick: (CrewRateItem) -> Unit
) : RecyclerView.Adapter<CrewRateAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCrewRateBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(
        private val binding: ItemCrewRateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CrewRateItem) {
            binding.tvCrewUsername.text = item.username

            if (!item.avatarUrl.isNullOrEmpty()) {
                Glide.with(binding.ivCrewAvatar)
                    .load(item.avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_circle_profile)
                    .into(binding.ivCrewAvatar)
            } else {
                binding.ivCrewAvatar.setImageResource(R.drawable.ic_circle_profile)
            }

            if (item.rate != null) {
                val filled = item.rate.rating.toInt().coerceIn(0, 5)
                binding.tvCrewStars.text = "\u2605".repeat(filled) + "\u2606".repeat(5 - filled)
                binding.tvCrewStars.setTextColor(0xFFFFD700.toInt())
            } else {
                binding.tvCrewStars.text = "Sin valorar"
                binding.tvCrewStars.setTextColor(0x55FFFFFF)
            }

            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
