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
 * The language of the interface. The texts are the strings of the resources (`composeResources/values-<code>/strings.xml`), which
 * follow the default locale: the app sets it from [language] (see `Main.kt`) and draws its windows again when it changes.
 */
object I18n {
    /** What the user chose: [SYSTEM] or the code of a language. A state, so that what shows a text is drawn again when it changes. */
    var setting: String by mutableStateOf(SYSTEM)

    const val SYSTEM = "system"

    /** The language the texts are shown in now. */
    val language: Language
        get() = if (setting == SYSTEM) Language.ofSystem() else Language.byCode(setting) ?: Language.EN
}
