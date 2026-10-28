package com.example.customme

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.customme.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PostAdapter

    /** Все публикации из Firestore. */
    private var allPosts: List<Post> = emptyList()

    /** Текущие фильтры ленты. */
    private var filter = PostFilter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        adapter = PostAdapter(
            showFilters = true,
            // В ленте сложность не показываем.
            showComplexity = false,
            onPostClick = { post -> openPost(post) },
            onMenuClick = { post -> openPostMenu(post) },
            onFiltersClick = { openFilters() }
        )

        val layoutManager = GridLayoutManager(requireContext(), 2)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position == 0) 2 else 1
            }
        }

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setPadding(0, 0, 0, 0)

        loadPosts()
    }

    private fun loadPosts() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    PostRepository.observeFeed().collect { posts ->
                        if (_binding == null) return@collect

                        allPosts = posts
                        render()
                    }
                } catch (error: Exception) {
                    if (_binding != null) {
                        allPosts = emptyList()
                        render()

                        binding.tvEmpty.text =
                            "не удалось загрузить публикации\n${FirebaseErrorMapper.message(error)}"
                    }
                }
            }
        }
    }

    /** Показывает ленту с учётом выбранных фильтров. */
    private fun render() {
        if (_binding == null) return

        binding.progress.isVisible = false

        val visiblePosts = filter.apply(allPosts)
        adapter.submitList(visiblePosts)
        adapter.setFiltersCount(filter.activeCount)

        binding.tvEmpty.isVisible = visiblePosts.isEmpty()
        binding.tvEmpty.text = if (filter.isActive) {
            "по выбранным фильтрам\nничего не найдено"
        } else {
            "пока нет публикаций\nстаньте первым!"
        }
    }

    private fun openFilters() {
        PostFiltersDialog.show(this, filter) { newFilter ->
            filter = newFilter
            render()
        }
    }

    private fun openPost(post: Post) {
        (activity as? MainActivity)?.openFragment(
            PostDetailFragment.newInstance(post.id),
            addToBackStack = true
        )
    }

    private fun openPostMenu(post: Post) {
        val isOwner = post.authorId == FirebaseAuth.getInstance().currentUser?.uid
        if (!isOwner) {
            openPost(post)
            return
        }

        PostActions.showOwnerMenu(this, post) {
            (activity as? MainActivity)?.openFragment(
                CreateFragment.newInstance(post.id),
                addToBackStack = true
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
