package su.demands.dayz_workbench_tool.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.path.*

class ConfigManager(subDir: String) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val configDir = Path(System.getenv("APPDATA")).resolve(".dayzworkbenchtool/v2/$subDir")
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

    companion object {
        val App = ConfigManager("app")
        val LayoutEditor = ConfigManager("layout-editor")

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