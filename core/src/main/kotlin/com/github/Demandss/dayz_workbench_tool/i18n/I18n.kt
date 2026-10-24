package com.github.demandss.dayz_workbench_tool.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** A language of the interface. */
enum class Language(
    val code: String,
    /** The name of the language in that language, as the list of languages shows it. */
    val nativeName: String,
) {
    EN("en", "English"),
    RU("ru", "Русский"),
    UK("uk", "Українська"),
    DE("de", "Deutsch"),
    FR("fr", "Français"),
    ES("es", "Español"),
    PT("pt", "Português"),
    ZH("zh", "简体中文"),
    ;

    companion object {
        /** The language with the code [code], or null. */
        fun byCode(code: String): Language? = entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }

        /** The language of the system when the interface has it, English otherwise. */
        fun ofSystem(locale: Locale = Locale.getDefault()): Language = byCode(locale.language) ?: EN
    }
}

/**
 * The language of the interface. The texts in the code are written in Russian and stand for their own keys: a text is looked up in
 * the table of the chosen language, then in English, and shown as it is when neither has it. So a text that was not translated
 * yet still shows, and the code reads as before.
 */
object I18n {
    /** What the user chose: [SYSTEM] or the code of a language. A state, so that what shows a text is drawn again when it changes. */
    var setting: String by mutableStateOf(SYSTEM)

    const val SYSTEM = "system"

    /** The language the texts are shown in now. */
    val language: Language
        get() = if (setting == SYSTEM) Language.ofSystem() else Language.byCode(setting) ?: Language.EN

    fun translate(
        text: String,
        args: Array<out Any?>,
    ): String {
        val language = language
        val translated =
            if (language == Language.RU) {
                text
            } else {
                Translations.of(language)[text] ?: Translations.of(Language.EN)[text] ?: text
            }
        if (args.isEmpty()) return translated
        var out = translated
        args.forEachIndexed { i, arg -> out = out.replace("{$i}", arg.toString()) }
        return out
    }
}

/** The text in the language of the interface; `{0}`, `{1}` in it are replaced by [args]. */
fun tr(
    text: String,
    vararg args: Any?,
): String = I18n.translate(text, args)

/**
 * Marks a text that is a key of the translations but is shown later, through [tr] with a variable (the name of a page that an enum
 * holds, for instance). It is the text itself; the check of the translations reads these marks as it reads the calls of [tr].
 */
fun trKey(text: String): String = text
