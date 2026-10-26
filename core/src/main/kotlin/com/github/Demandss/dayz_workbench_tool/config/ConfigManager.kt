package com.github.demandss.dayz_workbench_tool.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.io.path.*

/**
 * One settings file: `<baseDir>/<subDir>/settings.json`. [baseDir] is the versioned folder in %APPDATA% unless a test gives its own.
 */
class ConfigManager<T>(
    subDir: String,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
    baseDir: java.nio.file.Path = defaultBaseDir(),
) {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    private val configDir = baseDir.resolve(subDir)
    private val configFile = configDir.resolve("settings.json")

    /** A file next to the settings (`mcp.json` of the layout editor), in the same versioned folder. */
    fun fileIn(name: String) = configDir.resolve(name)

    /** A text value of the settings file by its key, whether or not the config class still has it; null when there is none. */
    fun readRawString(key: String): String? =
        runCatching {
            (json.parseToJsonElement(configFile.readText()) as? kotlinx.serialization.json.JsonObject)
                ?.get(key)
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.takeIf { it.isString }
                ?.content
        }.getOrNull()

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
        /** `%APPDATA%/.dayzworkbenchtool/v2`: where the settings of the programs are. */
        fun defaultBaseDir(): java.nio.file.Path = Path(System.getenv("APPDATA")).resolve(".dayzworkbenchtool/v2")

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
