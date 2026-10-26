package com.github.demandss.dayz_workbench_tool.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties

/** A release on GitHub: its version without the leading `v`, and the page to download it from. */
data class ReleaseInfo(
    val version: String,
    val url: String,
)

/** The version of the program that is running. */
object AppVersion {
    /** "dev" when the build did not fill the version in. */
    val installed: String by lazy {
        runCatching {
            AppVersion::class.java
                .getResourceAsStream("/app-version.properties")
                ?.use { Properties().apply { load(it) }.getProperty("version") }
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("$") }
            ?: "dev"
    }
}

/** Asks GitHub for the latest release. Nothing else is sent: no version, no settings, no identifier. */
object UpdateChecker {
    const val RELEASES_URL = "https://github.com/Demandss/dayz-workbench-tool-v2/releases"
    private const val LATEST_URL = "https://api.github.com/repos/Demandss/dayz-workbench-tool-v2/releases/latest"
    private val timeout = Duration.ofSeconds(5)
    private val json = Json { ignoreUnknownKeys = true }

    /** The latest release, or null when it cannot be told: no network, the repository is private, the limit of GitHub is used up. Blocks. */
    fun latest(): ReleaseInfo? =
        runCatching {
            val client =
                HttpClient
                    .newBuilder()
                    .connectTimeout(timeout)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
            val request =
                HttpRequest
                    .newBuilder(URI(LATEST_URL))
                    .timeout(timeout)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "dayz-workbench-tool")
                    .GET()
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) parse(response.body()) else null
        }.getOrNull()

    internal fun parse(body: String): ReleaseInfo? {
        val release = json.parseToJsonElement(body).jsonObject
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: RELEASES_URL
        return ReleaseInfo(tag.removePrefix("v"), url)
    }

    /** Whether [latest] is a higher version than [installed]; false when either is not a number like `1.2.3` (a "dev" build). */
    fun isNewer(
        latest: String,
        installed: String,
    ): Boolean {
        val a = numbers(latest) ?: return false
        val b = numbers(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int>? = version.removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }
}

/** What the windows show: the release that is newer than the installed one, or null. */
object UpdateStatus {
    var available: ReleaseInfo? by mutableStateOf(null)
        private set

    suspend fun refresh(installed: String = AppVersion.installed) {
        val latest = withContext(Dispatchers.IO) { UpdateChecker.latest() }
        available = latest?.takeIf { UpdateChecker.isNewer(it.version, installed) }
    }
}
