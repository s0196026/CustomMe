package com.example.customme

/**
 * Набор фильтров ленты: выбранные ярлыки (теги) и уровни сложности.
 * Обе группы могут содержать несколько значений; пустая группа не фильтрует.
 */
data class PostFilter(
    val tags: Set<String> = emptySet(),
    val complexities: Set<String> = emptySet()
) {

    /** Количество применённых значений — столько фильтров отображается на кнопке. */
    val activeCount: Int
        get() = tags.size + complexities.size

    val isActive: Boolean
        get() = activeCount > 0

    fun matches(post: Post): Boolean {
        val matchesTags = tags.isEmpty() || post.tags.any { tag -> tag in tags }

        val matchesComplexity = complexities.isEmpty() ||
            PostComplexity.normalize(post.complexity) in complexities

        return matchesTags && matchesComplexity
    }

    fun apply(posts: List<Post>): List<Post> {
        return if (isActive) posts.filter(::matches) else posts
    }
}
