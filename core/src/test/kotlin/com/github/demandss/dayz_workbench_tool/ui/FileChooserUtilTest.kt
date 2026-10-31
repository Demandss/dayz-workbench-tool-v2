package com.github.demandss.dayz_workbench_tool.ui

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pickers hand a title to PowerShell, and the title is translated now. These are the texts that broke them: letters of another
 * alphabet (the script was read in the code page of the system), curly quotes and an apostrophe (they end a string).
 */
class FileChooserUtilTest {
    private val awkward = "Выберите «локальный» мод: l'été, ‘x’ “y” ’s, C:\\Моды\\Ä"

    private fun onWindows() = assumeTrue(System.getProperty("os.name").startsWith("Windows"))

    @Test
    fun `a quote inside a text is written twice`() {
        assertEquals("'it''s'", psQuote("it's"))
        assertEquals("''", psQuote(""))
        // PowerShell reads the curly quotes as the plain one
        assertEquals("'‘‘x’’'", psQuote("‘x’"))
    }

    @Test
    fun `the title and the folder of a file dialog are quoted`() {
        val script = fileDialogScript("l'été", "exe", "C:\\O'Brien")
        assertTrue("\$d.Title = 'l''été';" in script, script)
        assertTrue("\$d.InitialDirectory = 'C:\\O''Brien';" in script, script)
    }

    @Test
    fun `a script file keeps any text that is written into it`() {
        onWindows()
        val file = createTempFile("dayz_test_", ".ps1").toFile()
        try {
            writePowerShellFile(file, UTF8_OUTPUT + "Write-Output ${psQuote(awkward)}")
            assertEquals(awkward, runPs("-ExecutionPolicy", "Bypass", "-File", file.absolutePath))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a path with letters of another alphabet comes back as it is`() {
        onWindows()
        assertEquals(
            "D:\\Моды\\Тест",
            runPs("-Command", "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding \$false; Write-Output 'D:\\Моды\\Тест'"),
        )
    }

    @Test
    fun `an error of the script is not taken for an answer`() {
        onWindows()
        assertNull(runPs("-Command", "Write-Output 'a string that is not closed"))
        assertNull(runPs("-Command", "exit 3"))
        assertNull(runPs("-Command", ""))
    }

    @Test
    fun `the file of a script starts with the mark of UTF-8`() {
        val file = File.createTempFile("dayz_test_", ".ps1")
        try {
            writePowerShellFile(file, "x")
            val bytes = file.readBytes()
            assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
        } finally {
            file.delete()
        }
    }
}
