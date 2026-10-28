package com.example.customme

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.customme.databinding.ItemCarouselImageBinding

/**
 * Адаптер карусели фотографий публикации.
 * Если фотографий нет, показывает одну страницу-заглушку.
 */
class PostCarouselAdapter(
    private val imageUrls: List<String> = emptyList(),
    private val onClick: () -> Unit = {}
) : RecyclerView.Adapter<PostCarouselAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemCarouselImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val url = imageUrls.getOrNull(position)

        if (url.isNullOrBlank()) {
            holder.binding.image.setImageResource(android.R.drawable.ic_menu_gallery)
        } else {
            holder.binding.image.load(Cloudinary.optimized(url, 1200)) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_gallery)
            }
        }

        holder.binding.image.setOnClickListener { onClick() }
    }

    override fun getItemCount(): Int = imageUrls.size.coerceAtLeast(1)

    class ImageViewHolder(val binding: ItemCarouselImageBinding) :
        RecyclerView.ViewHolder(binding.root)
}
