package com.example.customme

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.customme.databinding.FragmentCreateBinding
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class CreateFragment : Fragment(R.layout.fragment_create) {

    private var _binding: FragmentCreateBinding? = null
    private val binding get() = _binding!!

    private var postId: String? = null
    private var originalPost: Post? = null

    private val selectedTags = mutableListOf<String>()
    private lateinit var tagsAdapter: ArrayAdapter<String>

    private val selectedImages = mutableListOf<EditorImage>()
    private lateinit var imagesAdapter: PostImagesAdapter
    private lateinit var pickImages: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        postId = arguments?.getString(ARG_POST_ID)?.takeIf { it.isNotBlank() }

        pickImages = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)
        ) { uris ->
            addSelectedImages(uris)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateBinding.bind(view)

        setupComplexity()
        setupTags()
        setupImages()
        setupListeners()

        if (postId != null) {
            loadPost()
        }
    }

    private fun setupComplexity() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            PostComplexity.ALL
        )
        binding.etComplexity.setAdapter(adapter)
        binding.etComplexity.setOnItemClickListener { _, _, _, _ ->
            binding.tilComplexity.error = null
        }
    }

    private fun setupTags() {
        tagsAdapter = TagsAdapter(requireContext())
        binding.etTags.setAdapter(tagsAdapter)

        binding.etTags.setOnClickListener { binding.etTags.showDropDown() }
        binding.etTags.setOnItemClickListener { _, _, position, _ ->
            val tag = tagsAdapter.getItem(position) ?: return@setOnItemClickListener
            toggleTag(tag)
            binding.etTags.setText("", false)
            binding.etTags.post {
                if (_binding != null) binding.etTags.showDropDown()
            }
        }

        renderTagChips()
    }

    private fun toggleTag(tag: String) {
        if (!selectedTags.remove(tag)) {
            selectedTags.add(tag)
        }
        tagsAdapter.notifyDataSetChanged()
        renderTagChips()
    }

    private fun renderTagChips() {
        binding.chipGroupTags.removeAllViews()

        selectedTags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag
                isCheckable = false
                isCloseIconVisible = true
                chipStrokeWidth = 0f
                setChipBackgroundColorResource(R.color.green_primary)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setOnCloseIconClickListener {
                    selectedTags.remove(tag)
                    renderTagChips()
                }
            }
            binding.chipGroupTags.addView(chip)
        }
    }

    private fun setupImages() {
        imagesAdapter = PostImagesAdapter { image ->
            selectedImages.remove(image)
            imagesAdapter.submitList(selectedImages.toList())

            if (selectedImages.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Публикация должна содержать хотя бы одну фотографию",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.rvImages.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.rvImages.adapter = imagesAdapter
    }

    private fun setupListeners() {
        binding.etTitle.doAfterTextChanged { binding.tilTitle.error = null }

        binding.btnAddImages.setOnClickListener {
            if (selectedImages.size >= MAX_IMAGES) {
                Toast.makeText(
                    requireContext(),
                    "Можно добавить не больше $MAX_IMAGES фото",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnSave.setOnClickListener { savePost() }
    }

    private fun addSelectedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val freeSlots = MAX_IMAGES - selectedImages.size
        if (freeSlots <= 0) {
            Toast.makeText(
                requireContext(),
                "Можно добавить не больше $MAX_IMAGES фото",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        uris.take(freeSlots).forEach { uri ->
            selectedImages.add(EditorImage(uri = uri))
        }
        imagesAdapter.submitList(selectedImages.toList())

        if (uris.size > freeSlots) {
            Toast.makeText(
                requireContext(),
                "Добавлены только первые $freeSlots фото",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadPost() {
        val id = postId ?: return

        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val post = PostRepository.getPost(id)
                    ?: throw IllegalStateException("Публикация не найдена")

                val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                if (post.authorId != currentUid) {
                    throw IllegalStateException("Можно редактировать только свои публикации")
                }

                originalPost = post

                binding.etTitle.setText(post.title)
                binding.etDescription.setText(post.description)
                binding.etComplexity.setText(
                    PostComplexity.normalize(post.complexity)
                        .takeIf { value -> PostComplexity.isKnown(value) }
                        .orEmpty(),
                    false
                )

                selectedTags.clear()
                selectedTags.addAll(post.tags.distinct())
                tagsAdapter.notifyDataSetChanged()
                renderTagChips()

                selectedImages.clear()
                selectedImages.addAll(post.imageUrls.map { url -> EditorImage(url = url) })
                imagesAdapter.submitList(selectedImages.toList())

                binding.tvEditorTitle.text = "редактирование"
                binding.tvEditorSubtitle.text = "измените публикацию и сохраните"
                binding.btnSave.text = "сохранить изменения"

                setLoading(false)
            } catch (error: Exception) {
                setLoading(false)
                showError(FirebaseErrorMapper.message(error))
            }
        }
    }

    private fun savePost() {
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val tags = selectedTags.toList()
        val complexity = PostComplexity.normalize(
            binding.etComplexity.text?.toString().orEmpty()
        )

        if (!validateTitle(title)) return

        if (complexity !in PostComplexity.ALL) {
            binding.tilComplexity.error = "Выберите сложность"
            return
        }
        binding.tilComplexity.error = null

        if (postId != null && originalPost == null) {
            showError("Публикация ещё не загружена")
            return
        }

        if (selectedImages.isEmpty()) {
            showError("Добавьте хотя бы одну фотографию")
            return
        }

        binding.tvError.isVisible = false
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val post = originalPost

                if (post == null) {
                    PostRepository.createPost(
                        context = requireContext(),
                        title = title,
                        description = description,
                        tags = tags,
                        complexity = complexity,
                        imageUris = selectedImages.mapNotNull { it.uri }
                    )

                    Toast.makeText(
                        requireContext(),
                        "Публикация добавлена",
                        Toast.LENGTH_SHORT
                    ).show()

                    setLoading(false)
                    (activity as? MainActivity)?.showProfileTab()
                } else {
                    val keptImageUrls = selectedImages.mapNotNull { it.url }
                    val newImageUris = selectedImages.mapNotNull { it.uri }
                    val removedImageUrls = post.imageUrls.filterNot { it in keptImageUrls }

                    PostRepository.updatePost(
                        context = requireContext(),
                        post = post,
                        title = title,
                        description = description,
                        tags = tags,
                        complexity = complexity,
                        keptImageUrls = keptImageUrls,
                        newImageUris = newImageUris,
                        removedImageUrls = removedImageUrls
                    )

                    Toast.makeText(
                        requireContext(),
                        "Публикация обновлена",
                        Toast.LENGTH_SHORT
                    ).show()

                    setLoading(false)
                    (activity as? MainActivity)?.showProfileTab()
                }
            } catch (error: Exception) {
                setLoading(false)
                showError(FirebaseErrorMapper.message(error))
            }
        }
    }

    private fun validateTitle(title: String): Boolean {
        if (title.isBlank()) {
            binding.tilTitle.error = "Введите название"
            binding.etTitle.requestFocus()
            return false
        }
        if (title.length < 2) {
            binding.tilTitle.error = "Название должно содержать минимум 2 символа"
            binding.etTitle.requestFocus()
            return false
        }
        binding.tilTitle.error = null
        return true
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.btnSave.isEnabled = !loading
        binding.btnAddImages.isEnabled = !loading

        binding.tilTitle.isEnabled = !loading
        binding.tilDescription.isEnabled = !loading
        binding.tilTags.isEnabled = !loading
        binding.tilComplexity.isEnabled = !loading
        binding.etTags.isEnabled = !loading
        binding.etComplexity.isEnabled = !loading
        binding.chipGroupTags.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.isVisible = true
    }

    /** Показывает галочку у уже выбранных тегов и позволяет снять выбор повторным нажатием. */
    private inner class TagsAdapter(context: Context) : ArrayAdapter<String>(
        context,
        android.R.layout.simple_list_item_1,
        PostTags.ALL
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val tag = getItem(position).orEmpty()
            (view as? TextView)?.text = if (tag in selectedTags) "✓ $tag" else tag
            return view
        }

        override fun getDropDownView(
            position: Int,
            convertView: View?,
            parent: ViewGroup
        ): View {
            return getView(position, convertView, parent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_POST_ID = "post_id"
        private const val MAX_IMAGES = 5

        fun newInstance(postId: String? = null): CreateFragment {
            return CreateFragment().apply {
                arguments = Bundle().apply {
                    if (postId != null) putString(ARG_POST_ID, postId)
                }
            }
        }
    }
}
