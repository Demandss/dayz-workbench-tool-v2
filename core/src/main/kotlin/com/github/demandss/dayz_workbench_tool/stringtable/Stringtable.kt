package com.github.demandss.dayz_workbench_tool.stringtable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.demandss.dayz_workbench_tool.i18n.I18n
import com.github.demandss.dayz_workbench_tool.i18n.Language
import java.io.File

/** One row of a `stringtable.csv`: the key a layout writes after `#`, and the text in each language column. */
class StringtableEntry(
    val key: String,
    /** `game` for the vanilla table, otherwise the name of the mod folder. */
    val origin: String,
    /** The text by the name of the column in lower case (`english`, `russian`, `chinesesimp`). */
    val texts: Map<String, String>,
)

/** The column of the table that holds the texts of an interface language, or null when the game has no column for it. */
val Language.stringtableColumn: String?
    get() =
        when (this) {
            Language.EN -> "english"
            Language.RU -> "russian"
        }

/** The text of the entry in [language], or null when the language has no column or the cell is empty. */
fun StringtableEntry.textIn(language: Language): String? =
    language.stringtableColumn
        ?.let { texts[it] }
        ?.takeIf { it.isNotEmpty() }

/**
 * Reads a `stringtable.csv` of the game or of a mod. The first row names the columns (`Language`, `original`, `english`,
 * `czech`, ...; a mod may have fewer, in capitals, with a trailing comma or with spaces in a name). Cells may be in quotes with
 * `""` inside and with line breaks.
 */
object StringtableCsv {
    fun parse(
        text: String,
        origin: String,
    ): List<StringtableEntry> {
        val rows = rows(text.removePrefix(""))
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { normalize(it) }
        val out = ArrayList<StringtableEntry>(rows.size)
        for (row in rows.drop(1)) {
            val key = row.firstOrNull()?.trim().orEmpty()
            // A repeated header row (a table glued from two) or a blank line is not an entry
            if (key.isEmpty() || key.equals("language", ignoreCase = true)) continue
            val texts = HashMap<String, String>()
            for (i in 1 until minOf(row.size, header.size)) {
                if (header[i].isNotEmpty()) texts[header[i]] = row[i]
            }
            out += StringtableEntry(key, origin, texts)
        }
        return out
    }

    /** `CHINESE SIMP.` and `chinesesimp` are the same column. */
    private fun normalize(name: String) = name.trim().lowercase().filter { it.isLetterOrDigit() }

    private fun rows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0

        fun endCell() {
            row.add(cell.toString())
            cell.setLength(0)
        }

        fun endRow() {
            endCell()
            if (row.any { it.isNotEmpty() }) rows.add(row)
            row = ArrayList()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    cell.append('"')
                    i++
                }

                c == '"' -> {
                    quoted = !quoted
                }

                !quoted && c == ',' -> {
                    endCell()
                }

                !quoted && c == '\n' -> {
                    endRow()
                }

                !quoted && c == '\r' -> {}

                else -> {
                    cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}

/** Finds the string tables in a folder of the game or of a mod. */
object StringtableCatalog {
    /** True for `stringtable.csv` and the spelling `stringtables.csv` that some mods use. */
    fun isStringtable(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("stringtable") && lower.endsWith(".csv")
    }

    /** Every string table in [dir] and below it, with [origin] as the source of all of its entries. */
    fun scan(
        dir: File,
        origin: String,
        maxDepth: Int = 8,
    ): List<StringtableEntry> {
        val out = mutableListOf<StringtableEntry>()

        fun visit(
            folder: File,
            depth: Int,
        ) {
            val files = runCatching { folder.listFiles() }.getOrNull() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    if (depth < maxDepth && !file.name.startsWith(".")) visit(file, depth + 1)
                } else if (isStringtable(file.name)) {
                    runCatching { out += StringtableCsv.parse(file.readText(Charsets.UTF_8), origin) }
                }
            }
        }
        visit(dir, 0)
        return out
    }
}

/**
 * The string tables of the game and of the connected mods, looked up by the layout drawing. A text `#STR_Craft` is shown as the
 * text of the key in the language of the interface; a key that is not found, a language without a column or an empty cell leave
 * the text as it is.
 */
object StringtableRegistry {
    /** Changes when the tables change, so that what shows a text is drawn again. */
    private var version by mutableStateOf(0)

    private var byKey: Map<String, StringtableEntry> = emptyMap()

    /** Every entry, vanilla first, for the picker. */
    var entries: List<StringtableEntry> = emptyList()
        private set

    fun set(all: List<StringtableEntry>) {
        entries = all
        // The game keeps the first of two equal keys; keys do not depend on the case
        val map = HashMap<String, StringtableEntry>(all.size * 2)
        for (e in all) map.putIfAbsent(e.key.lowercase(), e)
        byKey = map
        version++
    }

    /** The text of [key] (without the `#`) in the language of the interface, or null to leave the text alone. */
    fun translate(key: String): String? {
        version
        val entry = byKey[key.trim().lowercase()] ?: return null
        return entry.textIn(I18n.language)
    }

    /** The entry of [key], or null. */
    fun find(key: String): StringtableEntry? = byKey[key.trim().lowercase()]
}
