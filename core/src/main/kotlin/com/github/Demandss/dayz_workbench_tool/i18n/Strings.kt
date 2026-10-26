package com.github.demandss.dayz_workbench_tool.i18n

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * A string of the resources for code that is not composable: a notification, a menu built in a state class, a click handler.
 * In a composable function use `stringResource` instead.
 */
fun str(
    resource: StringResource,
    vararg args: Any,
): String = runBlocking { getString(resource, *args) }
