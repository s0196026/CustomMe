package com.example.customme

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.customme.databinding.ItemEditorImageBinding

data class EditorImage(
    val uri: Uri? = null,
    val url: String? = null
)

class PostImagesAdapter(
    private val onRemove: (EditorImage) -> Unit
) : RecyclerView.Adapter<PostImagesAdapter.ImageViewHolder>() {

    private var images: List<EditorImage> = emptyList()

    fun submitList(newImages: List<EditorImage>) {
        images = newImages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemEditorImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val item = images[position]

        holder.binding.image.load(item.url ?: item.uri) {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_menu_gallery)
        }

        holder.binding.btnRemove.setOnClickListener { onRemove(item) }
    }

    override fun getItemCount(): Int = images.size

    class ImageViewHolder(val binding: ItemEditorImageBinding) :
        RecyclerView.ViewHolder(binding.root)
}
