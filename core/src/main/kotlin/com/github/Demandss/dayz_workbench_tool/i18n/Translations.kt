package com.github.demandss.dayz_workbench_tool.i18n

/** The tables of translations: the Russian text of the interface to its text in a language. */
internal object Translations {
    private val tables: Map<Language, Map<String, String>> by lazy {
        mapOf(
            Language.EN to translationsEn,
            Language.UK to translationsUk,
            Language.DE to translationsDe,
            Language.FR to translationsFr,
            Language.ES to translationsEs,
            Language.PT to translationsPt,
            Language.ZH to translationsZh,
        )
    }

    fun of(language: Language): Map<String, String> = tables[language].orEmpty()

    /** Every text that has at least a table entry in English: what the other languages are checked against. */
    val keys: Set<String> get() = of(Language.EN).keys
}
