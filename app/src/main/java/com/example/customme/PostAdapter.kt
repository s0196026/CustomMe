package com.example.customme

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.customme.databinding.ItemFiltersBinding
import com.example.customme.databinding.ItemPostBinding

class PostAdapter(
    private val showFilters: Boolean = true,
    private val showComplexity: Boolean = true,
    private val onPostClick: (Post) -> Unit = {},
    private val onMenuClick: (Post) -> Unit = onPostClick,
    private val onFiltersClick: () -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val typeFilters = 0
    private val typePost = 1

    private var posts: List<Post> = emptyList()
    private var filtersCount: Int = 0

    private val headerCount: Int
        get() = if (showFilters) 1 else 0

    inner class FiltersViewHolder(val binding: ItemFiltersBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class PostViewHolder(val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var carouselAdapter: PostCarouselAdapter? = null
    }

    fun submitList(newPosts: List<Post>) {
        posts = newPosts
        notifyDataSetChanged()
    }

    /** Обновляет счётчик применённых фильтров на шапке ленты. */
    fun setFiltersCount(count: Int) {
        if (filtersCount == count) return

        filtersCount = count
        if (showFilters && hasObservers()) {
            notifyItemChanged(0)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (showFilters && position == 0) typeFilters else typePost
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == typeFilters) {
            val holder = FiltersViewHolder(ItemFiltersBinding.inflate(inflater, parent, false))
            holder.binding.btnFilters.setOnClickListener { onFiltersClick() }
            holder
        } else {
            val holder = PostViewHolder(ItemPostBinding.inflate(inflater, parent, false))

            holder.binding.postImages.registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        val count = holder.carouselAdapter?.itemCount ?: 0
                        if (count > 1) {
                            holder.binding.tvImageCounter.text = "${position + 1}/$count"
                        }
                    }
                }
            )

            holder
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is FiltersViewHolder -> bindFilters(holder)

            is PostViewHolder -> bindPost(
                holder,
                posts[position - headerCount]
            )
        }
    }

    private fun bindFilters(holder: FiltersViewHolder) {
        holder.binding.tvFiltersLabel.text = if (filtersCount > 0) {
            "Фильтры ($filtersCount)"
        } else {
            "Фильтры"
        }
    }

    private fun bindPost(holder: PostViewHolder, post: Post) {
        val images = post.imageUrls

        with(holder.binding) {
            postTitle.text = post.title

            val stars = PostComplexity.normalize(post.complexity)
            postComplexity.text = stars
            postComplexity.isVisible = showComplexity && stars.isNotBlank()

            val carouselAdapter = PostCarouselAdapter(images) { onPostClick(post) }
            holder.carouselAdapter = carouselAdapter
            postImages.adapter = carouselAdapter
            postImages.setCurrentItem(0, false)

            tvImageCounter.isVisible = images.size > 1
            tvImageCounter.text = "1/${images.size}"

            root.setOnClickListener { onPostClick(post) }
            btnMore.setOnClickListener { onMenuClick(post) }
            btnHeart.setOnClickListener { onPostClick(post) }
        }
    }

    override fun getItemCount(): Int = posts.size + headerCount
}
