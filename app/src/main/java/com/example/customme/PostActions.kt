package com.example.customme

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Действия владельца публикации: редактирование и удаление. */
object PostActions {

    fun showOwnerMenu(fragment: Fragment, post: Post, onEdit: () -> Unit) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(post.title.ifBlank { "публикация" })
            .setItems(arrayOf("редактировать", "удалить публикацию")) { _, which ->
                when (which) {
                    0 -> onEdit()
                    1 -> confirmDelete(fragment, post)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmDelete(fragment: Fragment, post: Post) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Удалить публикацию?")
            .setMessage("Это действие нельзя отменить.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ -> delete(fragment, post) }
            .show()
    }

    private fun delete(fragment: Fragment, post: Post) {
        if (!fragment.isAdded) return

        val context = fragment.requireContext().applicationContext
        fragment.lifecycleScope.launch {
            try {
                PostRepository.deletePost(post)
                Toast.makeText(context, "Публикация удалена", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    FirebaseErrorMapper.message(error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
