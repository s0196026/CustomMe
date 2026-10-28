package com.example.customme

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.customme.databinding.FragmentPostDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PostDetailFragment : Fragment(R.layout.fragment_post_detail) {

    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!

    private var postId: String? = null
    private var post: Post? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        postId = arguments?.getString(ARG_POST_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPostDetailBinding.bind(view)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnEdit.setOnClickListener {
            post?.let { currentPost ->
                (activity as? MainActivity)?.openFragment(
                    CreateFragment.newInstance(currentPost.id),
                    addToBackStack = true
                )
            }
        }

        binding.btnDelete.setOnClickListener { confirmDelete() }

        binding.imagesPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val count = binding.imagesPager.adapter?.itemCount ?: 0
                    if (count > 1) {
                        binding.tvImageCounter.text = "${position + 1}/$count"
                    }
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            loadPost()
        }
    }

    private fun loadPost() {
        val id = postId ?: run {
            showError("Публикация не найдена")
            return
        }

        binding.tvError.isVisible = false
        binding.btnEdit.isVisible = false
        binding.btnDelete.isVisible = false
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val loadedPost = PostRepository.getPost(id)
                    ?: throw IllegalStateException("Публикация не найдена")

                post = loadedPost
                bindPost(loadedPost)

                val isOwner = loadedPost.authorId ==
                    FirebaseAuth.getInstance().currentUser?.uid
                binding.btnEdit.isVisible = isOwner
                binding.btnDelete.isVisible = isOwner

                setLoading(false)
            } catch (error: Exception) {
                setLoading(false)
                showError(FirebaseErrorMapper.message(error))
            }
        }
    }

    private fun bindPost(post: Post) {
        binding.tvPostTitle.text = post.title

        binding.tvAuthor.text = "автор: ${
            post.authorName.takeIf { it.isNotBlank() } ?: "пользователь"
        }"

        if (post.createdAt > 0L) {
            val formatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            binding.tvDate.text = formatter.format(Date(post.createdAt))
            binding.tvDate.isVisible = true
        } else {
            binding.tvDate.isVisible = false
        }

        val stars = PostComplexity.normalize(post.complexity)
        if (stars.isNotBlank()) {
            binding.tvComplexity.text = stars
            binding.tvComplexity.contentDescription = "сложность: $stars"
            binding.tvComplexity.isVisible = true
        } else {
            binding.tvComplexity.isVisible = false
        }

        if (post.tags.isNotEmpty()) {
            binding.tvTags.text = post.tags.joinToString("   ") { tag -> "#$tag" }
            binding.tvTags.isVisible = true
        } else {
            binding.tvTags.isVisible = false
        }

        if (post.description.isNotBlank()) {
            binding.tvDescription.text = post.description
            binding.tvDescription.isVisible = true
        } else {
            binding.tvDescription.isVisible = false
        }

        binding.imagesContainer.isVisible = post.imageUrls.isNotEmpty()
        binding.tvImageCounter.isVisible = post.imageUrls.size > 1
        binding.tvImageCounter.text = "1/${post.imageUrls.size}"

        if (post.imageUrls.isNotEmpty()) {
            binding.imagesPager.adapter = PostCarouselAdapter(post.imageUrls)
            binding.imagesPager.setCurrentItem(0, false)
        }
    }

    private fun confirmDelete() {
        val currentPost = post ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить публикацию?")
            .setMessage("Это действие нельзя отменить.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ -> deletePost(currentPost) }
            .show()
    }

    private fun deletePost(post: Post) {
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                PostRepository.deletePost(post)
                Toast.makeText(
                    requireContext(),
                    "Публикация удалена",
                    Toast.LENGTH_SHORT
                ).show()
                parentFragmentManager.popBackStack()
            } catch (error: Exception) {
                setLoading(false)
                showError(FirebaseErrorMapper.message(error))
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.btnEdit.isEnabled = !loading
        binding.btnDelete.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_POST_ID = "post_id"

        fun newInstance(postId: String): PostDetailFragment {
            return PostDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_POST_ID, postId)
                }
            }
        }
    }
}
