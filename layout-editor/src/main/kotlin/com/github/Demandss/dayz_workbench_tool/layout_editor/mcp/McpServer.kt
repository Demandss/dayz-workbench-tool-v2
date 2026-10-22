package com.github.demandss.dayz_workbench_tool.layout_editor.mcp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * The MCP server of the layout editor: Streamable HTTP on the local machine only, JSON answers (no streaming), so that an
 * assistant can read and change the layout that is open and look at it. One server per process; it serves the editor state
 * that is attached to it.
 */
object McpHost {
    private const val PORT_TRIES = 10
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var server: HttpServer? = null

    @Volatile
    private var tools: McpTools? = null

    /** The address the server listens on, or null while it is not running. */
    var url: String? by mutableStateOf(null)
        private set

    /** Why the server could not start, or null. */
    var lastError: String? by mutableStateOf(null)
        private set

    /** The latest calls, newest first, for the preferences window. */
    val calls = mutableStateListOf<String>()

    internal fun log(text: String) {
        Snapshot.withMutableSnapshot {
            calls.add(0, text)
            while (calls.size > 40) calls.removeAt(calls.lastIndex)
        }
    }

    /** Serves [state] from now on and starts the server when it is switched on and not running. */
    @Synchronized
    fun attach(state: LayoutEditorState) {
        tools = McpTools(state)
        if (state.mcpEnabled && server == null) start(state.mcpPort)
    }

    /** Stops serving [state] (the editor window was closed). The server itself keeps running for the next window. */
    @Synchronized
    fun detach(state: LayoutEditorState) {
        if (tools?.state === state) tools = null
    }

    /** Takes the switch and the port of [state] into use: restarts the server or stops it. */
    @Synchronized
    fun apply(state: LayoutEditorState) {
        stop()
        if (state.mcpEnabled) start(state.mcpPort)
    }

    private fun stop() {
        server?.stop(0)
        server = null
        url = null
    }

    private fun start(first: Int) {
        lastError = null
        for (port in first until first + PORT_TRIES) {
            val created =
                runCatching { HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0) }.getOrNull() ?: continue
            created.createContext("/mcp") { exchange -> runCatching { handle(exchange) }.onFailure { exchange.close() } }
            created.executor = Executors.newFixedThreadPool(4) { r -> Thread(r, "layout-editor-mcp").apply { isDaemon = true } }
            created.start()
            server = created
            url = "http://127.0.0.1:$port/mcp"
            writeDiscoveryFile(port)
            return
        }
        lastError = "Порты $first–${first + PORT_TRIES - 1} заняты"
    }

    /** Tells other programs where the server is: `%APPDATA%/.dayzworkbenchtool/layout-editor/mcp.json`. */
    private fun writeDiscoveryFile(port: Int) {
        runCatching {
            val dir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), ".dayzworkbenchtool/layout-editor")
            dir.mkdirs()
            File(dir, "mcp.json").writeText("""{"url":"http://127.0.0.1:$port/mcp","port":$port,"pid":${ProcessHandle.current().pid()}}""")
        }
    }

    private fun handle(exchange: HttpExchange) {
        // Only the local machine: a page in a browser must not be able to drive the editor
        val host =
            exchange.requestHeaders
                .getFirst("Host")
                .orEmpty()
                .substringBefore(':')
        val origin = exchange.requestHeaders.getFirst("Origin")
        if (host !in setOf("127.0.0.1", "localhost") || origin != null) {
            reply(exchange, 403, "forbidden")
            return
        }
        when (exchange.requestMethod) {
            "POST" -> {
                val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                val response = runCatching { process(json.parseToJsonElement(body)) }.getOrElse { error(null, -32700, "Parse error") }
                if (response == null) {
                    exchange.sendResponseHeaders(202, -1)
                    exchange.close()
                } else {
                    reply(exchange, 200, response.toString(), "application/json")
                }
            }

            "DELETE" -> {
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }

            else -> {
                exchange.responseHeaders.add("Allow", "POST, DELETE")
                reply(exchange, 405, "method not allowed")
            }
        }
    }

    private fun reply(
        exchange: HttpExchange,
        code: Int,
        text: String,
        type: String = "text/plain",
    ) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "$type; charset=utf-8")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun error(
        id: JsonElement?,
        code: Int,
        message: String,
    ): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            put(
                "error",
                buildJsonObject {
                    put("code", code)
                    put("message", message)
                },
            )
        }

    /** One JSON-RPC message. Null for a notification, which gets no answer; an array is a batch. */
    private fun process(message: JsonElement): JsonElement? {
        if (message is JsonArray) {
            val answers = message.mapNotNull { process(it) }
            return if (answers.isEmpty()) null else JsonArray(answers)
        }
        val request = message.jsonObject
        val id = request["id"]
        val method = request["method"]?.jsonPrimitive?.contentOrNull ?: return null
        if (id == null) return null
        val params = request["params"] as? JsonObject ?: buildJsonObject { }
        val result: JsonElement =
            when (method) {
                "initialize" -> {
                    buildJsonObject {
                        val asked = params["protocolVersion"]?.jsonPrimitive?.contentOrNull
                        put("protocolVersion", if (asked in SUPPORTED_VERSIONS) asked else SUPPORTED_VERSIONS.first())
                        put("capabilities", buildJsonObject { put("tools", buildJsonObject { }) })
                        put(
                            "serverInfo",
                            buildJsonObject {
                                put("name", "dayz-layout-editor")
                                put("version", "1.0.0")
                            },
                        )
                        put(
                            "instructions",
                            "Tools to read and edit the layout open in the DayZ Layout Editor, change the view and take screenshots. " +
                                "Widgets are found by id, by name or by a path such as rootFrame/Header/CloseImg.",
                        )
                    }
                }

                "ping" -> {
                    buildJsonObject { }
                }

                "tools/list" -> {
                    buildJsonObject { put("tools", JsonArray(McpTools.definitions())) }
                }

                "tools/call" -> {
                    val current = tools ?: return error(id, -32000, "The layout editor window is not open")
                    val name = params["name"]?.jsonPrimitive?.contentOrNull ?: return error(id, -32602, "Missing tool name")
                    current.call(name, params["arguments"] as? JsonObject ?: buildJsonObject { })
                }

                else -> {
                    return error(id, -32601, "Method not found: $method")
                }
            }
        return buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }
    }

    private val SUPPORTED_VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

internal fun JsonObject.bool(key: String): Boolean? = string(key)?.toBooleanStrictOrNull()

internal fun JsonObject.int(key: String): Int? = string(key)?.toDoubleOrNull()?.toInt()

/** Puts the address of the MCP server on the clipboard and says so. */
internal fun LayoutEditorState.copyMcpAddress(address: String) {
    java.awt.Toolkit
        .getDefaultToolkit()
        .systemClipboard
        .setContents(java.awt.datatransfer.StringSelection(address), null)
    notify("Адрес MCP скопирован: $address", com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind.SUCCESS)
}
