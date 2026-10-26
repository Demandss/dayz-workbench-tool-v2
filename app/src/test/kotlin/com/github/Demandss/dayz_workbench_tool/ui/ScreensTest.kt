package com.github.demandss.dayz_workbench_tool.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.github.demandss.dayz_workbench_tool.config.AppConfig
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.config.LayoutEditorConfig
import com.github.demandss.dayz_workbench_tool.config.ModEntry
import com.github.demandss.dayz_workbench_tool.i18n.I18n
import com.github.demandss.dayz_workbench_tool.manager.ToolsManager
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.ModType
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.state.Screen
import com.github.demandss.dayz_workbench_tool.ui.theme.AppTheme
import java.util.Locale
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The windows are drawn without a screen and used the way a person does: they have to come up, show their texts in the language
 * of the interface, and answer a click. It is what a change of the texts, of the settings or of a component can break unseen.
 */
@OptIn(ExperimentalTestApi::class)
class ScreensTest {
    private val base = createTempDirectory("dayz-screens")
    private val app = ConfigManager("app", AppConfig.serializer(), ::AppConfig, base)
    private val editor = ConfigManager("layout-editor", LayoutEditorConfig.serializer(), ::LayoutEditorConfig, base)

    // A path of a mod that need not exist: under the folder of the test, so that it is nobody's
    private fun mod(name: String) = base.resolve("mods").resolve(name).toString()

    private val localeBefore: Locale = Locale.getDefault()
    private val toolsBefore = ToolsManager.toolsDir
    private val languageBefore = I18n.setting

    @BeforeTest
    fun english() {
        Locale.setDefault(Locale.ENGLISH)
        I18n.setting = "en"
        ToolsManager.toolsDir = base.resolve("tools")
    }

    @AfterTest
    fun restore() {
        Locale.setDefault(localeBefore)
        I18n.setting = languageBefore
        ToolsManager.toolsDir = toolsBefore
    }

    private fun state(config: AppConfig = AppConfig(setupDone = true, language = "en")): AppState {
        app.save(config)
        return AppState(app, editor)
    }

    /** A text may be on the screen twice (a menu and a panel have the same word): it only has to be there. */
    private fun ComposeUiTest.assertShown(text: String) {
        assertTrue(onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(), "\"$text\" is not on the screen")
    }

    @Test
    fun `the settings window shows its groups and opens another one on a click`() =
        runComposeUiTest {
            val state = state().also { it.navigate(Screen.SETTINGS) }
            setContent { AppTheme { SettingsScreen(state) } }

            assertShown("Global")
            assertShown("App")
            assertShown("Layout Editor")
            // The first section of the first group
            assertShown("Interface")
            assertShown("Language")

            onNodeWithText("Layout Editor").performClick()
            assertShown("MCP")
            assertShown("General")
        }

    @Test
    fun `a section of the list opens its settings`() =
        runComposeUiTest {
            val state = state().also { it.navigate(Screen.SETTINGS) }
            setContent { AppTheme { SettingsScreen(state) } }

            onNodeWithText("Folders").performClick()
            assertShown("Game folder")
            assertShown("Work drive")
        }

    @Test
    fun `the search finds a setting by a word of another language and says when there is none`() =
        runComposeUiTest {
            val state = state().also { it.navigate(Screen.SETTINGS) }
            setContent { AppTheme { SettingsScreen(state) } }

            onNode(hasSetTextAction()).performTextInput("язык")
            assertShown("Language")

            onNode(hasSetTextAction()).performTextInput("xqzxqz")
            assertShown("Nothing found")
        }

    @Test
    fun `the main window shows the tools, the mods panel and the way to its settings`() =
        runComposeUiTest {
            val state = state()
            setContent { AppTheme { MainContent(state) } }

            assertShown("Tools")
            assertShown("Workbench")
            assertShown("Layout Editor")
            assertShown("Mods")
            assertShown("Client")
            assertShown("Server")
            assertShown("Mods settings")
        }

    @Test
    fun `a path typed into the mods panel is added as a mod of that side`() =
        runComposeUiTest {
            val state = state()
            setContent { AppTheme { MainContent(state) } }

            // The first field and the first plus belong to the client side, the second ones to the server
            onAllNodes(hasSetTextAction())[0].performTextInput(mod("TypedClient"))
            onAllNodesWithText("+")[0].performClick()
            onAllNodes(hasSetTextAction())[1].performTextInput(mod("@TypedServer"))
            onAllNodesWithText("+")[1].performClick()

            assertEquals(listOf("TypedClient" to ModSide.CLIENT, "@TypedServer" to ModSide.SERVER), state.mods.map { it.name to it.side })
            assertEquals(ModType.WORKSHOP, state.mods.last().type)
            assertEquals(
                mod("TypedClient"),
                app
                    .load()
                    .clientMods
                    .single()
                    .path,
            )
        }

    @Test
    fun `a blank path is not added as a mod`() =
        runComposeUiTest {
            val state = state()
            setContent { AppTheme { MainContent(state) } }

            onAllNodesWithText("+")[0].performClick()
            assertEquals(emptyList(), state.mods.toList())
        }

    @Test
    fun `the mods window lists the mods of both sides`() =
        runComposeUiTest {
            val state =
                state(
                    AppConfig(
                        setupDone = true,
                        language = "en",
                        clientMods = listOf(ModEntry(mod("MyClientMod"))),
                        serverMods = listOf(ModEntry(mod("MyServerMod"))),
                    ),
                ).also { it.navigate(Screen.MODS) }
            setContent { AppTheme { ModsScreen(state) } }

            assertShown("MyClientMod")
            assertShown("MyServerMod")
        }

    @Test
    fun `the texts follow the language of the interface`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            I18n.setting = "ru"
            val state = state(AppConfig(setupDone = true, language = "ru")).also { it.navigate(Screen.SETTINGS) }
            setContent { AppTheme { SettingsScreen(state) } }

            assertShown("Язык")
            assertShown("Интерфейс")
        }
}
