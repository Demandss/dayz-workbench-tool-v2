package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

private val TRANSLIT =
    mapOf(
        'а' to "a",
        'б' to "b",
        'в' to "v",
        'г' to "g",
        'д' to "d",
        'е' to "e",
        'ё' to "e",
        'ж' to "zh",
        'з' to "z",
        'и' to "i",
        'й' to "y",
        'к' to "k",
        'л' to "l",
        'м' to "m",
        'н' to "n",
        'о' to "o",
        'п' to "p",
        'р' to "r",
        'с' to "s",
        'т' to "t",
        'у' to "u",
        'ф' to "f",
        'х' to "h",
        'ц' to "ts",
        'ч' to "ch",
        'ш' to "sh",
        'щ' to "sch",
        'ъ' to "",
        'ы' to "y",
        'ь' to "",
        'э' to "e",
        'ю' to "yu",
        'я' to "ya",
    )

/** True when the name has anything but Latin letters, digits and underscores. */
internal fun hasInvalidNameChars(name: String): Boolean = name.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '_' }

/**
 * A widget name that script can find: Cyrillic is written in Latin letters, every other character becomes an underscore,
 * and the result is never empty.
 */
internal fun sanitizeWidgetName(raw: String): String {
    val out = StringBuilder()
    for (c in raw.trim()) {
        val lower = c.lowercaseChar()
        val latin = TRANSLIT[lower]
        when {
            latin != null -> out.append(if (c.isUpperCase()) latin.replaceFirstChar { it.uppercase() } else latin)
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' -> out.append(c)
            else -> out.append('_')
        }
    }
    val cleaned = out.toString().replace(Regex("_+"), "_").trim('_')
    return cleaned.ifEmpty { "widget" }
}
