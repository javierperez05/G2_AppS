package com.example.cosmos.ui.News

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  CrewRateItem — modelo local del bottom sheet
 *      Combina userId, username, rate (puede ser null si no ha
 *      valorado) y avatarUrl. Se construye en NewsFragment a partir
 *      de los datos que devuelve CrewUiState.Ready.
 *
 *  onItemClick
 *      Al pulsar un miembro, NewsFragment abre el overlay de detalle
 *      con la rate completa (estrellas, comentario, avatar).
 * ═══════════════════════════════════════════════════════════════════
 */

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

            loadCrewAvatar(binding.ivCrewAvatar, item.avatarUrl)

            if (item.rate != null) {
                val filled = item.rate.rating.toInt().coerceIn(0, 5)
                binding.tvCrewStars.text = "\u2605".repeat(filled) + "\u2606".repeat(5 - filled)
                binding.tvCrewStars.setTextColor(0xFFFFD700.toInt())
            } else {
                binding.tvCrewStars.text = binding.root.context.getString(R.string.no_rated)
                binding.tvCrewStars.setTextColor(0x55FFFFFF)
            }

            binding.root.setOnClickListener { onClick(item) }
        }

        private fun loadCrewAvatar(iv: com.google.android.material.imageview.ShapeableImageView, urlOrBase64: String?) {
            if (urlOrBase64.isNullOrBlank()) {
                iv.setImageResource(R.drawable.ic_circle_profile)
                return
            }
            val trimmed = urlOrBase64.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                Glide.with(iv).load(trimmed).circleCrop()
                    .placeholder(R.drawable.ic_circle_profile)
                    .error(R.drawable.ic_circle_profile)
                    .into(iv)
                return
            }
            try {
                val bytes = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
                Glide.with(iv).load(bytes).circleCrop()
                    .placeholder(R.drawable.ic_circle_profile)
                    .error(R.drawable.ic_circle_profile)
                    .into(iv)
            } catch (e: IllegalArgumentException) {
                iv.setImageResource(R.drawable.ic_circle_profile)
            }
        }
    }
}
