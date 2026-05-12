package com.example.cosmos.ui.News

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
        Glide.with(holder.binding.ivCarouselPhoto)
            .load(urls[position])
            .centerCrop()
            .into(holder.binding.ivCarouselPhoto)
    }

    override fun getItemCount() = urls.size
}
