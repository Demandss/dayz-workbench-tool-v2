package com.github.demandss.dayz_workbench_tool.layout_editor.mcp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.config.LayoutEditorConfig
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.resources.*
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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Properties
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

    /**
     * What a request has to carry in `Authorization: Bearer <token>`. It is made once and kept with the settings of the editor, so
     * that a client that was given it keeps working after a restart; [regenerateToken] makes a new one. It is also written to the
     * discovery file, which only the user can read, so a program that merely knows the port cannot drive the editor.
     */
    var token: String? by mutableStateOf(null)
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
        token = null
        deleteDiscoveryFile()
    }

    private fun start(first: Int) {
        lastError = null
        for (port in first until first + PORT_TRIES) {
            val created =
                runCatching { HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0) }.getOrNull() ?: continue
            created.createContext("/mcp") { exchange ->
                runCatching { handle(exchange) }.onFailure {
                    // A failure outside the tools (a broken connection) cannot be answered; one that can be is
                    runCatching { reply(exchange, 500, "internal error: ${it.message ?: it.javaClass.simpleName}") }
                    exchange.close()
                }
            }
            created.executor = Executors.newFixedThreadPool(4) { r -> Thread(r, "layout-editor-mcp").apply { isDaemon = true } }
            created.start()
            server = created
            url = "http://127.0.0.1:$port/mcp"
            token = storedToken()
            boundPort = port
            writeDiscoveryFile(port, token.orEmpty())
            return
        }
        lastError = str(Res.string.mcp_ports_busy, first, first + PORT_TRIES - 1)
    }

    private var boundPort = 0

    /** The token kept in the settings of the editor; one is made and kept when there is none. */
    private fun storedToken(): String {
        val config = runCatching { ConfigManager.LayoutEditor.load() }.getOrDefault(LayoutEditorConfig())
        if (config.mcpToken.length >= 32) return config.mcpToken
        val fresh = newToken()
        runCatching { ConfigManager.LayoutEditor.save(config.copy(mcpToken = fresh)) }
        return fresh
    }

    /** Makes a new token and keeps it; the clients that had the old one have to be given the new one. */
    @Synchronized
    fun regenerateToken() {
        val fresh = newToken()
        runCatching { ConfigManager.LayoutEditor.save(ConfigManager.LayoutEditor.load().copy(mcpToken = fresh)) }
        if (server != null) {
            token = fresh
            writeDiscoveryFile(boundPort, fresh)
        }
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** The discovery file lies with the other settings of the layout editor: `%APPDATA%/.dayzworkbenchtool/v2/layout-editor/mcp.json`. */
    private fun discoveryFile(): File = ConfigManager.LayoutEditor.fileIn("mcp.json").toFile()

    private var hookInstalled = false

    /** Tells other programs where the server is and what token it wants; removed again when the server stops or the editor exits. */
    private fun writeDiscoveryFile(
        port: Int,
        token: String,
    ) {
        runCatching {
            val file = discoveryFile()
            file.parentFile.mkdirs()
            // The file of older versions lay one folder up, without the token: it would point at a dead port
            File(System.getenv("APPDATA") ?: System.getProperty("user.home"), ".dayzworkbenchtool/layout-editor/mcp.json").delete()
            file.writeText(
                """{"url":"http://127.0.0.1:$port/mcp","port":$port,"pid":${ProcessHandle.current().pid()},"token":"$token"}""",
            )
            if (!hookInstalled) {
                hookInstalled = true
                Runtime.getRuntime().addShutdownHook(Thread { deleteDiscoveryFile() })
            }
        }
    }

    /** Deletes the discovery file, unless it was written by another running editor (its pid is not ours). */
    private fun deleteDiscoveryFile() {
        runCatching {
            val file = discoveryFile()
            if (!file.isFile) return
            val pid =
                Regex("\"pid\"\\s*:\\s*(\\d+)")
                    .find(file.readText())
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
            if (pid == null || pid == ProcessHandle.current().pid()) file.delete()
        }
    }

    /** True when the request carries the token of this run (the comparison takes the same time for every token). */
    private fun authorized(exchange: HttpExchange): Boolean {
        val expected = token ?: return false
        val given =
            exchange.requestHeaders
                .getFirst("Authorization")
                .orEmpty()
                .removePrefix("Bearer ")
                .trim()
        return MessageDigest.isEqual(given.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
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
        if (!authorized(exchange)) {
            exchange.responseHeaders.add("WWW-Authenticate", "Bearer")
            reply(exchange, 401, "unauthorized: send the token of mcp.json as Authorization: Bearer <token>")
            return
        }
        when (exchange.requestMethod) {
            "POST" -> {
                val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull()
                // -32700 only for a body that is not JSON; a failure inside is an internal error
                val response =
                    if (parsed == null) {
                        error(null, -32700, "Parse error")
                    } else {
                        runCatching {
                            process(
                                parsed,
                            )
                        }.getOrElse { error(null, -32603, "Internal error: ${it.message ?: it.javaClass.simpleName}") }
                    }
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
                        put(
                            "capabilities",
                            buildJsonObject {
                                put("tools", buildJsonObject { })
                                put("prompts", buildJsonObject { })
                            },
                        )
                        put(
                            "serverInfo",
                            buildJsonObject {
                                put("name", "dayz-layout-editor")
                                put("version", serverVersion)
                            },
                        )
                        put("instructions", McpGuide.instructions)
                    }
                }

                "ping" -> {
                    buildJsonObject { }
                }

                "prompts/list" -> {
                    McpGuide.listPrompts()
                }

                "prompts/get" -> {
                    val name = params["name"]?.jsonPrimitive?.contentOrNull ?: return error(id, -32602, "Missing prompt name")
                    McpGuide.getPrompt(name, params["arguments"] as? JsonObject) ?: return error(id, -32602, "Unknown prompt: $name")
                }

                "tools/list" -> {
                    buildJsonObject { put("tools", JsonArray(McpTools.definitions())) }
                }

                "tools/call" -> {
                    val current = tools ?: return answer(id, toolError(WINDOW_CLOSED))
                    val name = params["name"]?.jsonPrimitive?.contentOrNull ?: return error(id, -32602, "Missing tool name")
                    // A tool that throws is an error of that tool (isError), not a dropped connection
                    runCatching { current.call(name, params["arguments"] as? JsonObject ?: buildJsonObject { }) }.getOrElse {
                        toolError("${it.javaClass.simpleName}: ${it.message.orEmpty()}")
                    }
                }

                else -> {
                    return error(id, -32601, "Method not found: $method")
                }
            }
        return answer(id, result)
    }

    private fun answer(
        id: JsonElement,
        result: JsonElement,
    ): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }

    /** The result of a tool that failed: the assistant reads the text and can act on it. */
    private fun toolError(text: String): JsonObject =
        buildJsonObject {
            put("isError", true)
            put(
                "content",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("type", "text")
                            put("text", text)
                        },
                    ),
                ),
            )
        }

    private const val WINDOW_CLOSED =
        "The layout editor window is not open. Ask the person to open it with the Layout Editor button of the DayZ Workbench Tool, then try again."

    /** The version of the program, from the build (app-version.properties); "dev" when the build did not fill it in. */
    internal val serverVersion: String =
        runCatching {
            McpHost::class.java
                .getResourceAsStream(
                    "/app-version.properties",
                )?.use { Properties().apply { load(it) }.getProperty("version") }
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("$") }
            ?: "dev"

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
    notify(str(Res.string.mcp_address_copied, address), com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind.SUCCESS)
}
