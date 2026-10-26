package com.github.demandss.dayz_workbench_tool.ui

import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.ModType
import com.github.demandss.dayz_workbench_tool.model.Modification

/** What the list of mods is narrowed to; a mod has to pass every filter that is set. The search by name is separate. */
internal data class ModFilters(
    /** Null: both sides. */
    val side: ModSide? = null,
    /** True: only the enabled mods, false: only the disabled ones, null: both. */
    val enabled: Boolean? = null,
    /** Null: Workshop and local mods. */
    val type: ModType? = null,
    /** Only the mods whose folder is not there. */
    val onlyMissing: Boolean = false,
) {
    val isActive: Boolean get() = activeCount > 0

    /** How many filters are set. */
    val activeCount: Int get() = listOf(side != null, enabled != null, type != null, onlyMissing).count { it }

    fun matches(
        mod: Modification,
        missingPaths: Set<String>,
    ): Boolean =
        (side == null || mod.side == side) &&
            (enabled == null || mod.enabled == enabled) &&
            (type == null || mod.type == type) &&
            (!onlyMissing || mod.path in missingPaths)

    /** The filter with [side] set, or cleared when it is the one that is set already. */
    fun toggleSide(side: ModSide): ModFilters = copy(side = if (this.side == side) null else side)

    fun toggleEnabled(value: Boolean): ModFilters = copy(enabled = if (enabled == value) null else value)

    fun toggleType(type: ModType): ModFilters = copy(type = if (this.type == type) null else type)

    fun toggleMissing(): ModFilters = copy(onlyMissing = !onlyMissing)
}

/** The mods of [mods] that pass [filters] and have [search] in the name. */
internal fun filterMods(
    mods: List<Modification>,
    filters: ModFilters,
    search: String,
    missingPaths: Set<String>,
): List<Modification> =
    mods.filter { filters.matches(it, missingPaths) && (search.isBlank() || it.name.contains(search.trim(), ignoreCase = true)) }
