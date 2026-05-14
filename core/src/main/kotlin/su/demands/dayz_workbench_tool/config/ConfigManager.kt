package su.demands.dayz_workbench_tool.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.path.*

object ConfigManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val configDir = Path(System.getenv("APPDATA")).resolve(".dayzworkbenchtool/v2")
    private val configFile = configDir.resolve("settings.json")

    fun load(): AppConfig {
        if (!configFile.exists()) return AppConfig()
        return runCatching { json.decodeFromString<AppConfig>(configFile.readText()) }
            .getOrDefault(AppConfig())
    }

    fun save(config: AppConfig) {
        configDir.createDirectories()
        configFile.writeText(json.encodeToString(config))
    }
}