package com.example.customme

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.customme.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PostAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileBinding.bind(view)

        val currentUser = FirebaseAuth.getInstance().currentUser
        binding.tvUsername.text = currentUser?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.substringBefore("@")
            ?: "гость"

        adapter = PostAdapter(
            showFilters = false,
            onPostClick = { post -> openPost(post) },
            onMenuClick = { post -> openPostMenu(post) }
        )

        binding.profileRecycler.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.profileRecycler.adapter = adapter

        binding.btnMessages.setOnClickListener { showComingSoon("Сообщения") }
        binding.btnBoxes.setOnClickListener { showComingSoon("Ящики") }
        binding.btnNotifications.setOnClickListener { showComingSoon("Уведомления") }
        binding.btnCart.setOnClickListener { showComingSoon("Корзина") }
        binding.btnSettings.setOnClickListener { showSettings() }

        currentUser?.uid?.let { uid -> loadUserPosts(uid) }
    }

    private fun loadUserPosts(uid: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    PostRepository.observeUserPosts(uid).collect { posts ->
                        if (_binding == null) return@collect

                        adapter.submitList(posts)
                        binding.tvEmpty.isVisible = posts.isEmpty()
                    }
                } catch (error: Exception) {
                    if (_binding != null) {
                        adapter.submitList(emptyList())
                        binding.tvEmpty.isVisible = true
                        binding.tvEmpty.text = FirebaseErrorMapper.message(error)
                    }
                }
            }
        }
    }

    private fun openPost(post: Post) {
        (activity as? MainActivity)?.openFragment(
            PostDetailFragment.newInstance(post.id),
            addToBackStack = true
        )
    }

    private fun openPostMenu(post: Post) {
        PostActions.showOwnerMenu(this, post) {
            (activity as? MainActivity)?.openFragment(
                CreateFragment.newInstance(post.id),
                addToBackStack = true
            )
        }
    }

    private fun showComingSoon(section: String) {
        Toast.makeText(requireContext(), "$section: раздел в разработке", Toast.LENGTH_SHORT).show()
    }

    private fun showSettings() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Настройки")
            .setMessage("Выйти из аккаунта?")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Выйти") { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()

        val intent = Intent(requireContext(), AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
