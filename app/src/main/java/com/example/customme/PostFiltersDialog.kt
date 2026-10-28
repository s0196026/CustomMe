package com.example.customme

import android.content.Context
import android.content.DialogInterface
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Диалог фильтров ленты: ярлыки и сложность (можно выбрать несколько),
 * кнопки «Применить» и «Сбросить».
 */
object PostFiltersDialog {

    fun show(fragment: Fragment, current: PostFilter, onApply: (PostFilter) -> Unit) {
        val context = fragment.context ?: return
        val view = fragment.layoutInflater.inflate(R.layout.dialog_filters, null)

        val groupTags = view.findViewById<ChipGroup>(R.id.chipGroupTags)
        val groupComplexity = view.findViewById<ChipGroup>(R.id.chipGroupComplexity)

        PostTags.ALL.forEach { tag ->
            groupTags.addView(createChip(context, tag, isChecked = tag in current.tags))
        }

        PostComplexity.ALL.forEach { stars ->
            groupComplexity.addView(
                createChip(context, stars, isChecked = stars in current.complexities)
            )
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Фильтры")
            .setView(view)
            .setPositiveButton("Применить", null)
            .setNegativeButton("Сбросить", null)
            .create()

        dialog.setOnShowListener {
            // Кнопки обрабатываем сами, чтобы «Сбросить» очищал чипы, а не закрывал диалог.
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                onApply(
                    PostFilter(
                        tags = checkedValues(groupTags),
                        complexities = checkedValues(groupComplexity)
                    )
                )
                dialog.dismiss()
            }

            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                groupTags.clearCheck()
                groupComplexity.clearCheck()
            }
        }

        dialog.show()
    }

    private fun checkedValues(group: ChipGroup): Set<String> {
        val result = mutableSetOf<String>()

        for (index in 0 until group.childCount) {
            val chip = group.getChildAt(index) as? Chip ?: continue
            if (chip.isChecked) {
                chip.text?.toString()?.let { value -> result.add(value) }
            }
        }

        return result
    }

    private fun createChip(context: Context, text: String, isChecked: Boolean): Chip {
        return Chip(context).apply {
            this.text = text
            isCheckable = true
            this.isChecked = isChecked

            setChipBackgroundColorResource(R.color.chip_bg)
            setChipStrokeColorResource(R.color.chip_stroke)
            setTextColor(ContextCompat.getColorStateList(context, R.color.chip_text))
        }
    }
}
