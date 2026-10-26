package com.github.demandss.dayz_workbench_tool.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.io.path.*

class ConfigManager<T>(
    subDir: String,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
) {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    private val configDir = Path(System.getenv("APPDATA")).resolve(".dayzworkbenchtool/v2/$subDir")
    private val configFile = configDir.resolve("settings.json")

    /** A file next to the settings (`mcp.json` of the layout editor), in the same versioned folder. */
    fun fileIn(name: String) = configDir.resolve(name)

    fun load(): T {
        if (!configFile.exists()) return default()
        return runCatching { json.decodeFromString(serializer, configFile.readText()) }
            .getOrElse { default() }
    }

    fun save(config: T) {
        configDir.createDirectories()
        configFile.writeText(json.encodeToString(serializer, config))
    }

    companion object {
        val App = ConfigManager("app", AppConfig.serializer(), ::AppConfig)
        val LayoutEditor = ConfigManager("layout-editor", LayoutEditorConfig.serializer(), ::LayoutEditorConfig)

        init {
            val appdata = Path(System.getenv("APPDATA"))
            val legacyFile = appdata.resolve(".dayzworkbenchtool/v2/settings.json")
            val newAppFile = appdata.resolve(".dayzworkbenchtool/v2/app/settings.json")
            if (!newAppFile.exists() && legacyFile.exists()) {
                newAppFile.parent.createDirectories()
                legacyFile.copyTo(newAppFile)
            }
        }
    }
}
