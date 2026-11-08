package com.github.demandss.dayz_workbench_tool.state

import com.github.demandss.dayz_workbench_tool.config.AppConfig
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.config.LayoutEditorConfig
import com.github.demandss.dayz_workbench_tool.config.ModEntry
import com.github.demandss.dayz_workbench_tool.i18n.I18n
import com.github.demandss.dayz_workbench_tool.manager.PreparedWorkbench
import com.github.demandss.dayz_workbench_tool.manager.ToolsManager
import com.github.demandss.dayz_workbench_tool.manager.WorkbenchPreparer
import com.github.demandss.dayz_workbench_tool.manager.WorkspaceFailure
import com.github.demandss.dayz_workbench_tool.manager.WorkspacePreparationException
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.ModType
import com.github.demandss.dayz_workbench_tool.model.Modification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The state of the app over settings and tools folders of its own: nothing here touches the settings of the person who runs it. */
class AppStateTest {
    private val base: Path = createTempDirectory("dayz-state")
    private val app = ConfigManager("app", AppConfig.serializer(), ::AppConfig, base)
    private val editor = ConfigManager("layout-editor", LayoutEditorConfig.serializer(), ::LayoutEditorConfig, base)

    // Paths of mods that need not exist: under the folder of the test, so that they are nobody's
    private fun mod(name: String) = base.resolve("mods").resolve(name).toString()

    private fun workshop(name: String) =
        base
            .resolve("mods")
            .resolve("!Workshop")
            .resolve(name)
            .toString()

    private val toolsBefore = ToolsManager.toolsDir
    private val languageBefore = I18n.setting

    @BeforeTest
    fun point() {
        ToolsManager.toolsDir = base.resolve("tools")
    }

    @AfterTest
    fun restore() {
        ToolsManager.toolsDir = toolsBefore
        I18n.setting = languageBefore
    }

    private val prepared = PreparedWorkbench(listOf(base.resolve(".Workbench").toString()))

    private val workspace =
        object : WorkbenchPreparer {
            override fun prepare(
                config: AppConfig,
                mods: List<Modification>,
            ) = prepared

            override fun cleanupGameLinks() = Unit
        }

    private fun state() = AppState(app, editor, workspace, { _, _ -> }, {})

    @Test
    fun `a first start has the default settings and the language of the system`() {
        val state = state()
        assertEquals(I18n.SYSTEM, state.config.language)
        assertEquals(emptyList(), state.mods.toList())
        assertEquals(Screen.MAIN, state.currentScreen)
    }

    @Test
    fun `the language that the editor had is kept`() {
        base.resolve("layout-editor").createDirectories()
        base.resolve("layout-editor/settings.json").writeText("""{"language":"ru"}""")
        val state = state()
        assertEquals("ru", state.config.language)
        assertEquals("ru", I18n.setting)
        // And it is written, so the next start does not look at the editor again
        assertEquals("ru", app.load().language)
    }

    @Test
    fun `a saved language that the interface does not have becomes the language of the system`() {
        app.save(AppConfig(language = "de"))
        val state = state()
        assertEquals(I18n.SYSTEM, state.config.language)
        assertEquals(I18n.SYSTEM, app.load().language)
    }

    @Test
    fun `a mod that is added is kept in the settings`() {
        val state = state()
        state.addMod(Modification(mod("MyMod"), ModSide.CLIENT))
        state.addMod(Modification(workshop("@Other"), ModSide.SERVER))

        assertEquals(listOf(ModEntry(mod("MyMod"))), app.load().clientMods)
        assertEquals(listOf(ModEntry(workshop("@Other"))), app.load().serverMods)
        // A second state over the same settings sees the same mods
        assertEquals(listOf("MyMod", "@Other"), state().mods.map { it.name })
    }

    @Test
    fun `the type of a mod comes from its path`() {
        assertEquals(ModType.LOCAL, Modification(mod("MyMod"), ModSide.CLIENT).type)
        assertEquals(ModType.WORKSHOP, Modification(workshop("@Other"), ModSide.CLIENT).type)
        val state = state()
        state.addMod(Modification(workshop("@Other"), ModSide.CLIENT))
        assertEquals(ModType.WORKSHOP, state().mods.single().type)
    }

    @Test
    fun `a mod is switched off, moved to the other side and removed`() {
        val state = state()
        val mod = Modification(mod("MyMod"), ModSide.CLIENT)
        state.addMod(mod)

        state.toggleEnabled(mod)
        assertFalse(
            app
                .load()
                .clientMods
                .single()
                .enabled,
        )

        state.switchSide(state.mods.single())
        assertEquals(emptyList(), app.load().clientMods)
        assertEquals(
            mod("MyMod"),
            app
                .load()
                .serverMods
                .single()
                .path,
        )

        state.removeMod(state.mods.single())
        assertEquals(emptyList(), app.load().serverMods)
        assertEquals(emptyList(), state.mods.toList())
    }

    @Test
    fun `the settings that are saved reload the mods and write the settings of the tools`() =
        runBlocking {
            val state = state()
            state.start(this)
            try {
                state.updateConfig(
                    AppConfig(
                        setupDone = true,
                        gamePath = "",
                        workDrivePath = "P:",
                        clientMods = listOf(ModEntry(mod("A")), ModEntry(mod("B"), enabled = false)),
                        serverMods = listOf(ModEntry(mod("S"))),
                        language = "en",
                    ),
                )
                assertEquals(listOf("A", "B", "S"), state.mods.map { it.name })
                assertEquals("en", I18n.setting)
                assertEquals("en", app.load().language)

                state.awaitPreparation()
                val bat = base.resolve("tools/UserSettings.bat")
                assertTrue(bat.exists())
                val text = bat.readText()
                assertTrue(mod("A") in text && mod("S") in text, text)
                assertFalse(mod("B") in text, "a mod that is off is not given to the game")
            } finally {
                state.shutdown()
            }
        }

    @Test
    fun `the settings window opens on its first page unless the editor asks for its own`() {
        val state = state()
        state.settingsGroup = 1
        state.settingsSection = "game"
        state.navigate(Screen.SETTINGS)
        assertEquals(1, state.settingsGroup, "going to the settings keeps the page")
        state.navigate(Screen.MAIN)
        assertEquals(0, state.settingsGroup)
        assertEquals("", state.settingsSection)

        state.openLayoutEditorSettings()
        assertEquals(Screen.SETTINGS, state.currentScreen)
        assertEquals(2, state.settingsGroup)
    }

    @Test
    fun `only the settings of the editor that the person changed are saved`() {
        editor.save(LayoutEditorConfig(showGrid = true, gridSize = 10, windowWidth = 1500))
        val state = state()
        val opened = editor.load()
        // While the window was open the editor wrote its window size; the person changed the grid size only
        editor.save(opened.copy(windowWidth = 1800))
        val before = state.layoutEditorSettingsTick

        state.saveLayoutEditorSettings(opened, opened.copy(gridSize = 24))

        val saved = editor.load()
        assertEquals(24, saved.gridSize)
        assertEquals(1800, saved.windowWidth)
        assertTrue(saved.showGrid)
        assertEquals(before + 1, state.layoutEditorSettingsTick)
    }

    @Test
    fun `the state does not write outside the folders it was given`() =
        runBlocking {
            app.save(AppConfig(setupDone = true))
            val state = state()
            state.start(this)
            try {
                state.addMod(Modification(mod("MyMod"), ModSide.CLIENT))
                state.awaitPreparation()
                assertTrue(base.resolve("app/settings.json").exists())
                assertTrue(base.resolve("tools/UserSettings.bat").exists())
            } finally {
                state.shutdown()
            }
        }

    @Test
    fun `launch prepares the selected mods on an IO thread before Workbench and its scripts`() =
        runBlocking {
            val events = Collections.synchronizedList(mutableListOf<String>())
            val caller = Thread.currentThread()
            var preparationThread: Thread? = null
            val preparer =
                object : WorkbenchPreparer {
                    override fun prepare(
                        config: AppConfig,
                        mods: List<Modification>,
                    ): PreparedWorkbench {
                        preparationThread = Thread.currentThread()
                        assertEquals(listOf("MyMod"), mods.map { it.name })
                        events += "prepare"
                        return prepared
                    }

                    override fun cleanupGameLinks() = Unit
                }
            val state =
                AppState(
                    app,
                    editor,
                    preparer,
                    { _, result ->
                        assertEquals(prepared, result)
                        events += "workbench"
                    },
                    { events += "scripts" },
                )
            state.addMod(Modification(mod("MyMod"), ModSide.CLIENT))

            try {
                state.launchWorkbench(this).join()
                assertEquals(listOf("prepare", "workbench", "scripts"), events)
                assertTrue(preparationThread !== caller)
                assertFalse(state.isWorkbenchStarting)
                assertEquals(null, state.workbenchError)
            } finally {
                state.shutdown()
            }
        }

    @Test
    fun `failed preparation prevents Workbench and all custom scripts from starting`() =
        runBlocking {
            val failure = WorkspacePreparationException(WorkspaceFailure.UNAVAILABLE_PATH, listOf(mod("Missing")))
            val preparer =
                object : WorkbenchPreparer {
                    override fun prepare(
                        config: AppConfig,
                        mods: List<Modification>,
                    ): PreparedWorkbench = throw failure

                    override fun cleanupGameLinks() = Unit
                }
            var launches = 0
            var scripts = 0
            val state = AppState(app, editor, preparer, { _, _ -> launches++ }, { scripts++ })
            state.addMod(Modification(mod("Missing"), ModSide.CLIENT))
            state.updateConfig(state.config.copy(setupDone = true))

            try {
                state.launchWorkbench(this).join()
                assertEquals(0, launches)
                assertEquals(0, scripts)
                assertEquals(failure, state.workbenchError)
                assertFalse(state.isWorkbenchStarting)
                assertTrue("MODS_CLIENT=${mod("Missing")}\"" in base.resolve("tools/UserSettings.bat").readText())
                state.dismissWorkbenchError()
                assertEquals(null, state.workbenchError)
            } finally {
                state.shutdown()
            }
        }

    @Test
    fun `a failed Workbench process start does not run the custom scripts`() =
        runBlocking {
            val failure = IllegalStateException("process start failed")
            var scripts = 0
            val state = AppState(app, editor, workspace, { _, _ -> throw failure }, { scripts++ })

            try {
                state.launchWorkbench(this).join()
                assertEquals(0, scripts)
                assertEquals(failure.message, assertIs<IllegalStateException>(state.workbenchError).message)
            } finally {
                state.shutdown()
            }
        }

    @Test
    fun `a selection changed during preparation is prepared again before launch`() =
        runBlocking {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val preparations = Collections.synchronizedList(mutableListOf<List<String>>())
            var launched: List<String> = emptyList()
            val preparer =
                object : WorkbenchPreparer {
                    override fun prepare(
                        config: AppConfig,
                        mods: List<Modification>,
                    ): PreparedWorkbench {
                        preparations += mods.map { it.name }
                        if (preparations.size == 1) {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                        return prepared
                    }

                    override fun cleanupGameLinks() = Unit
                }
            val state =
                AppState(app, editor, preparer, { config, _ -> launched = config.clientMods.map { it.path } }, {})
            state.addMod(Modification(mod("A"), ModSide.CLIENT))

            try {
                val launch = state.launchWorkbench(this)
                assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
                state.addMod(Modification(mod("B"), ModSide.CLIENT))
                release.countDown()
                launch.join()
                assertEquals(listOf(listOf("A"), listOf("A", "B")), preparations)
                assertEquals(listOf(mod("A"), mod("B")), launched)
            } finally {
                release.countDown()
                state.shutdown()
            }
        }
}
