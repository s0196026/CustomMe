package com.example.customme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object PostRepository {

    private const val COLLECTION = "posts"
    private const val MAX_IMAGE_SIDE = 1600
    private const val JPEG_QUALITY = 85

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val posts by lazy { firestore.collection(COLLECTION) }

    fun observeFeed(limit: Long = 80): Flow<List<Post>> = callbackFlow {
        val listener = posts
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val result = snapshot?.documents
                    ?.mapNotNull { document -> document.toObject(Post::class.java) }
                    ?: emptyList()

                trySend(result)
            }

        awaitClose { listener.remove() }
    }

    fun observeUserPosts(authorId: String): Flow<List<Post>> = callbackFlow {
        val listener = posts
            .whereEqualTo("authorId", authorId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val result = snapshot?.documents
                    ?.mapNotNull { document -> document.toObject(Post::class.java) }
                    ?.sortedByDescending { post -> post.createdAt }
                    ?: emptyList()

                trySend(result)
            }

        awaitClose { listener.remove() }
    }

    suspend fun getPost(postId: String): Post? {
        return posts.document(postId).get().await().toObject(Post::class.java)
    }

    suspend fun createPost(
        context: Context,
        title: String,
        description: String,
        tags: List<String>,
        complexity: String,
        imageUris: List<Uri>
    ): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: error("Пользователь не авторизован")

        val imageUrls = imageUris.map { uri ->
            uploadImage(context, uri, user.uid)
        }

        val now = System.currentTimeMillis()
        val data = hashMapOf<String, Any>(
            "authorId" to user.uid,
            "authorName" to (
                user.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@").orEmpty()
                ),
            "title" to title,
            "description" to description,
            "tags" to tags,
            "complexity" to complexity,
            "imageUrls" to imageUrls,
            "createdAt" to now,
            "updatedAt" to now
        )

        return posts.add(data).await().id
    }

    suspend fun updatePost(
        context: Context,
        post: Post,
        title: String,
        description: String,
        tags: List<String>,
        complexity: String,
        keptImageUrls: List<String>,
        newImageUris: List<Uri>,
        removedImageUrls: List<String>
    ) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: error("Пользователь не авторизован")

        val uploadedUrls = newImageUris.map { uri ->
            uploadImage(context, uri, user.uid)
        }

        val data = hashMapOf<String, Any>(
            "title" to title,
            "description" to description,
            "tags" to tags,
            "complexity" to complexity,
            "imageUrls" to (keptImageUrls + uploadedUrls),
            "updatedAt" to System.currentTimeMillis()
        )

        posts.document(post.id).update(data).await()

        removedImageUrls.forEach { url -> deleteImage(url) }
    }

    suspend fun deletePost(post: Post) {
        posts.document(post.id).delete().await()
        post.imageUrls.forEach { url -> deleteImage(url) }
    }

    /** Сжимаем фото на устройстве и загружаем в Cloudinary; в Firestore попадает только ссылка. */
    private suspend fun uploadImage(context: Context, uri: Uri, uid: String): String {
        val bytes = withContext(Dispatchers.IO) {
            compressImage(context, uri)
        }
        return Cloudinary.upload(bytes)
    }

    private fun compressImage(context: Context, uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("Не удалось прочитать выбранное изображение")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                MAX_IMAGE_SIDE
            )
        }

        val input = context.contentResolver.openInputStream(uri)
            ?: error("Не удалось открыть выбранное изображение")

        val original = input.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("Не удалось прочитать выбранное изображение")

        val scale = minOf(
            1f,
            MAX_IMAGE_SIDE.toFloat() / maxOf(original.width, original.height).toFloat()
        )

        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt(),
                (original.height * scale).toInt(),
                true
            )
        } else {
            original
        }

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)

        if (bitmap !== original) bitmap.recycle()
        original.recycle()

        return output.toByteArray()
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sampleSize = 1
        while (maxOf(width, height) / sampleSize > maxSide) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Unsigned-загрузка не позволяет удалять файлы из Cloudinary (нужен api_secret, а ему не место в приложении).
     * Поэтому при удалении поста/фото ссылка просто исчезает из Firestore, а файл остаётся в Cloudinary
     * (его можно очистить вручную в консоли или через серверную функцию).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun deleteImage(url: String) = Unit
}
