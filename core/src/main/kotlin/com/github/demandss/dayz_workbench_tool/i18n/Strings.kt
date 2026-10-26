package com.github.demandss.dayz_workbench_tool.i18n

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.Locale

/**
 * A string of the resources for code that is not composable: a notification, a menu built in a state class, a click handler.
 * In a composable function use `stringResource` instead.
 */
fun str(
    resource: StringResource,
    vararg args: Any,
): String = synchronized(localeLock) { runBlocking { getString(resource, *args) } }

// Held while the default locale is changed, so that a string asked for on another thread meanwhile is not read in the wrong language
private val localeLock = Any()

/**
 * The text of a resource in every language of the interface, in the order of [Language.entries]. For the search of the settings,
 * where a person may type a word of another language than the one the interface is in. The resources follow the default locale,
 * so it is set for each language in turn and put back.
 */
fun strInEveryLanguage(resource: StringResource): List<String> =
    synchronized(localeLock) {
        val saved = Locale.getDefault()
        try {
            Language.entries.map { language ->
                Locale.setDefault(Locale.forLanguageTag(language.code))
                str(resource)
            }
        } finally {
            Locale.setDefault(saved)
        }
    }
