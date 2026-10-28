package com.example.customme

/**
 * Уровни сложности публикации.
 *
 * В новых публикациях хранится строка из звёзд, но старые записи могли быть
 * сохранены словами — [normalize] приводит их к актуальному виду.
 */
object PostComplexity {

    const val EASY = "★"
    const val MEDIUM = "★★"
    const val HARD = "★★★"

    val ALL = listOf(EASY, MEDIUM, HARD)

    fun normalize(value: String): String {
        return when (value.trim().lowercase()) {
            EASY, "1", "лёгкий", "легкий", "лёгкая", "легкая", "легко", "простой", "easy" -> EASY
            MEDIUM, "2", "средний", "средняя", "средне", "medium" -> MEDIUM
            HARD, "3", "сложный", "сложная", "сложно", "hard" -> HARD
            else -> value.trim()
        }
    }

    fun isKnown(value: String): Boolean = normalize(value) in ALL
}

/** Список тегов, доступных для выбора в редакторе публикации. */
object PostTags {

    val ALL = listOf(
        "аксессуар",
        "верх",
        "низ",
        "one-piece",
        "обувь",
        "милый",
        "панк",
        "футуристичный",
        "спортивный",
        "альтернативный",
        "винтаж",
        "гранж",
        "элегантный"
    )
}