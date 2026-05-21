package com.example.cosmos.ui.News

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemCarouselPhotoBinding

class PostImageCarouselAdapter(
    private val urls: List<String>
) : RecyclerView.Adapter<PostImageCarouselAdapter.VH>() {

    class VH(val binding: ItemCarouselPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCarouselPhotoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        // Each page fills the RecyclerView exactly
        binding.root.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.MATCH_PARENT
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        loadPhoto(holder.binding.ivCarouselPhoto, urls[position])
    }

    private fun loadPhoto(iv: android.widget.ImageView, urlOrBase64: String) {
        val trimmed = urlOrBase64.trim()
        // Si es URL, cargar directo
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            Glide.with(iv).load(trimmed).centerCrop()
                .error(R.drawable.ic_cosmos_critter)
                .into(iv)
            return
        }
        // Si no es URL, intentamos decodificar Base64
        try {
            val bytes = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
            Glide.with(iv).load(bytes).centerCrop()
                .error(R.drawable.ic_cosmos_critter)
                .into(iv)
        } catch (e: IllegalArgumentException) {
            // Base64 inválido -> fallback
            iv.setImageResource(R.drawable.ic_cosmos_critter)
        }
    }

    override fun getItemCount() = urls.size
}



