package com.github.demandss.dayz_workbench_tool.state

import androidx.compose.runtime.*
import com.github.demandss.dayz_workbench_tool.config.AppConfig
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.config.LayoutEditorConfig
import com.github.demandss.dayz_workbench_tool.config.ModEntry
import com.github.demandss.dayz_workbench_tool.config.withChanges
import com.github.demandss.dayz_workbench_tool.i18n.I18n
import com.github.demandss.dayz_workbench_tool.manager.ModificationManager
import com.github.demandss.dayz_workbench_tool.manager.PreparedWorkbench
import com.github.demandss.dayz_workbench_tool.manager.ProcessManager
import com.github.demandss.dayz_workbench_tool.manager.ToolsManager
import com.github.demandss.dayz_workbench_tool.manager.WorkbenchPreparer
import com.github.demandss.dayz_workbench_tool.manager.WorkbenchWorkspace
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.ModType
import com.github.demandss.dayz_workbench_tool.model.Modification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Screen { MAIN, SETTINGS, MODS }

/** The state of the app. The two config managers are the real ones; a test gives its own, over a folder of its own. */
class AppState(
    private val appConfig: ConfigManager<AppConfig> = ConfigManager.App,
    private val editorConfig: ConfigManager<LayoutEditorConfig> = ConfigManager.LayoutEditor,
    private val workspace: WorkbenchPreparer = WorkbenchWorkspace(appConfig.fileIn("managed-links.json")),
    private val workbenchLauncher: (AppConfig, PreparedWorkbench) -> Unit = { config, prepared ->
        ProcessManager.launchWorkbench(config, prepared)
    },
    private val customScriptsLauncher: (List<String>) -> Unit = ProcessManager::launchCustomScripts,
) {
    var config by mutableStateOf(appConfig.load())
        private set

    val mods = mutableStateListOf<Modification>()

    var currentScreen by mutableStateOf(Screen.MAIN)
    var showLayoutEditor by mutableStateOf(false)

    var workbenchError by mutableStateOf<Throwable?>(null)
        private set

    var isWorkbenchStarting by mutableStateOf(false)
        private set

    private val preparationMutex = Mutex()
    private var preparationScope: CoroutineScope? = null
    private var preparationJob: Job? = null
    private var launchJob: Job? = null
    private var toolsDeployed = false
    private var scriptsConfig: AppConfig? = null

    @Volatile
    private var revision = 0L

    @Volatile
    private var closing = false

    private data class PreparationSnapshot(
        val config: AppConfig,
        val mods: List<Modification>,
        val revision: Long,
    )

    init {
        // The language was a setting of the editor before: whoever chose one keeps it
        if (config.language == null) {
            val carried = editorConfig.readRawString("language") ?: I18n.SYSTEM
            config = config.copy(language = carried)
            appConfig.save(config)
        }
        // A saved language that the interface does not have (there were more of them once) becomes the language of the system
        if (config.language != I18n.settingOf(config.language)) {
            config = config.copy(language = I18n.settingOf(config.language))
            appConfig.save(config)
        }
        I18n.setting = config.language ?: I18n.SYSTEM
        loadModsFromConfig()
    }

    fun navigate(screen: Screen) {
        // The next visit to the settings starts on the first page again, not on the one the editor asked for
        if (screen != Screen.SETTINGS) {
            settingsGroup = 0
            settingsSection = ""
        }
        currentScreen = screen
    }

    /** The open group of the settings window: 0 Global, 1 App, 2 Layout Editor (the editor asks for it, also while the window is open). */
    var settingsGroup by mutableStateOf(0)

    /** The id of the open section of that group; empty opens the first. */
    var settingsSection by mutableStateOf("")

    /** Brings the settings window of the app to the Layout Editor group. */
    fun openLayoutEditorSettings() {
        settingsGroup = 2
        settingsSection = ""
        navigate(Screen.SETTINGS)
    }

    /** Changes when the settings of the layout editor were stored from the settings window, so that an open editor reads them. */
    var layoutEditorSettingsTick by mutableStateOf(0)
        private set

    /**
     * Stores the settings of the layout editor that were edited in the settings window: only those that [edited] changed
     * against [base] (what the window showed when it was opened). The rest stays as it is on the disk, because the editor writes
     * the window size, the sessions and its toggles itself while the window is open.
     */
    fun saveLayoutEditorSettings(
        base: LayoutEditorConfig,
        edited: LayoutEditorConfig,
    ) {
        editorConfig.save(editorConfig.load().withChanges(base, edited))
        layoutEditorSettingsTick++
    }

    fun updateConfig(newConfig: AppConfig) {
        config = newConfig
        revision++
        workbenchError = null
        I18n.setting = newConfig.language ?: I18n.SYSTEM
        appConfig.save(newConfig)
        loadModsFromConfig()
        requestPreparation()
    }

    fun addMod(mod: Modification) {
        mods += mod
        persistMods()
    }

    fun removeMod(mod: Modification) {
        mods -= mod
        persistMods()
    }

    fun toggleEnabled(mod: Modification) {
        val idx = mods.indexOf(mod)
        if (idx >= 0) {
            mods[idx] = mod.copy(enabled = !mod.enabled)
            persistMods()
        }
    }

    fun switchSide(mod: Modification) {
        val idx = mods.indexOf(mod)
        if (idx >= 0) {
            mods[idx] = mod.copy(side = if (mod.side == ModSide.CLIENT) ModSide.SERVER else ModSide.CLIENT)
            persistMods()
        }
    }

    private fun loadModsFromConfig() {
        mods.clear()
        config.clientMods.forEach { mods += it.toModification(ModSide.CLIENT) }
        config.serverMods.forEach { mods += it.toModification(ModSide.SERVER) }
    }

    private fun persistMods() {
        val clientEntries = mods.filter { it.side == ModSide.CLIENT }.map { it.toEntry() }
        val serverEntries = mods.filter { it.side == ModSide.SERVER }.map { it.toEntry() }
        config = config.copy(clientMods = clientEntries, serverMods = serverEntries)
        revision++
        workbenchError = null
        appConfig.save(config)
        requestPreparation()
    }

    /** Attaches preparation to the lifetime of the app window; constructing state never touches the work drive. */
    fun start(scope: CoroutineScope) {
        if (preparationScope != null || closing) return
        preparationScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        requestPreparation(deployOnly = !config.setupDone)
    }

    fun dismissWorkbenchError() {
        workbenchError = null
    }

    private fun snapshot() = PreparationSnapshot(config, mods.toList(), revision)

    private fun requestPreparation(deployOnly: Boolean = false) {
        val scope = preparationScope ?: return
        if (closing || (!config.setupDone && !deployOnly)) return
        val requested = snapshot()
        preparationJob =
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        preparationMutex.withLock {
                            if (closing || requested.revision != revision) return@withLock
                            deployTools()
                            if (!deployOnly) prepareLocked(requested)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (!closing && requested.revision == revision) workbenchError = failure
                }
            }
    }

    /** Waits for the latest scheduled preparation; useful for callers that need the generated tool settings. */
    suspend fun awaitPreparation() {
        do {
            val requested = preparationJob
            requested?.join()
        } while (requested !== preparationJob)
    }

    private fun deployTools() {
        if (!toolsDeployed) {
            ToolsManager.deploy()
            toolsDeployed = true
        }
    }

    /** Called only under preparationMutex on an IO dispatcher. */
    private fun prepareLocked(requested: PreparationSnapshot): PreparedWorkbench {
        deployTools()
        ToolsManager.writeUserSettings(requested.config, requested.mods)
        val prepared = workspace.prepare(requested.config, requested.mods)
        val previous = scriptsConfig
        if (previous == null ||
            previous.effectiveScriptsPath() != requested.config.effectiveScriptsPath() ||
            previous.gamePath != requested.config.gamePath
        ) {
            if (previous != null) ModificationManager.removeScriptsSymlink(previous.effectiveScriptsPath(), previous.gamePath)
            ModificationManager.createScriptsSymlink(requested.config.effectiveScriptsPath(), requested.config.gamePath)
            scriptsConfig = requested.config
        }
        return prepared
    }

    /** Revalidates the latest selection, then starts Workbench and its scripts in that order. */
    fun launchWorkbench(scope: CoroutineScope): Job {
        if (closing) return Job().also { it.cancel() }
        start(scope)
        launchJob?.takeIf { it.isActive }?.let { return it }
        val currentScope = preparationScope ?: return Job().also { it.cancel() }
        isWorkbenchStarting = true
        workbenchError = null
        val job =
            currentScope.launch {
                try {
                    while (!closing) {
                        val requested = snapshot()
                        val launched =
                            try {
                                withContext(Dispatchers.IO) {
                                    preparationMutex.withLock {
                                        if (closing || requested.revision != revision) return@withLock false
                                        val prepared = prepareLocked(requested)
                                        if (closing || requested.revision != revision) return@withLock false
                                        workbenchLauncher(requested.config, prepared)
                                        customScriptsLauncher(requested.config.customWorkbenchScripts)
                                        true
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                if (requested.revision != revision) continue
                                throw failure
                            }
                        if (launched) break
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (!closing) workbenchError = failure
                } finally {
                    isWorkbenchStarting = false
                }
            }
        launchJob = job
        return job
    }

    /** The manual cleanup tool shares the same ownership checks and serialization as preparation. */
    fun removeGameLinks() {
        val scope = preparationScope ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { preparationMutex.withLock { workspace.cleanupGameLinks() } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!closing) workbenchError = failure
            }
        }
    }

    /** The shutdown hook waits for in-flight IO before removing only game links; WorkDir survives. */
    fun shutdown() {
        closing = true
        preparationScope?.cancel()
        runBlocking(Dispatchers.IO) {
            preparationMutex.withLock {
                try {
                    workspace.cleanupGameLinks()
                } finally {
                    scriptsConfig?.let { ModificationManager.removeScriptsSymlink(it.effectiveScriptsPath(), it.gamePath) }
                }
            }
        }
    }

    private fun ModEntry.toModification(side: ModSide) =
        Modification(
            path = path,
            side = side,
            type = if ("@" in path) ModType.WORKSHOP else ModType.LOCAL,
            enabled = enabled,
            priority = priority,
        )

    private fun Modification.toEntry() = ModEntry(path, enabled, priority)
}
