package com.github.demandss.dayz_workbench_tool.manager

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal enum class JunctionKind { MISSING, DIRECTORY, JUNCTION, OTHER }

internal data class JunctionInfo(
    val kind: JunctionKind,
    val target: Path? = null,
)

/** Inspects the reparse type and target, and never removes a target directory recursively. */
internal object WindowsJunctions {
    fun inspect(path: Path): JunctionInfo {
        val attributes =
            try {
                Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            } catch (_: NoSuchFileException) {
                return JunctionInfo(JunctionKind.MISSING)
            }
        if (!attributes.isOther && !attributes.isSymbolicLink) {
            return JunctionInfo(if (attributes.isDirectory) JunctionKind.DIRECTORY else JunctionKind.OTHER)
        }
        val result =
            run(
                """
                ${pathVariable("p", path)}
                ${'$'}item = Get-Item -LiteralPath ${'$'}p -Force -ErrorAction Stop
                if (${'$'}item.LinkType -eq 'Junction') {
                    ${'$'}result = @{ kind = 'JUNCTION'; target = [string](@(${'$'}item.Target)[0]) }
                } else {
                    ${'$'}result = @{ kind = 'OTHER'; target = '' }
                }
                [Console]::Write((${'$'}result | ConvertTo-Json -Compress))
                """.trimIndent(),
            )
        val value = Json.parseToJsonElement(result).jsonObject
        val kind = JunctionKind.valueOf(value.getValue("kind").jsonPrimitive.content)
        val target =
            value["target"]
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotEmpty() }
                ?.let(Path::of)
        return JunctionInfo(kind, target)
    }

    fun create(
        link: Path,
        target: Path,
    ) {
        // -Target expands wildcards. Resolve '.' from the literal target in this child process instead.
        run(
            """
            ${pathVariable("p", link)}
            ${pathVariable("t", target)}
            Set-Location -LiteralPath ${'$'}t
            [Environment]::CurrentDirectory = ${'$'}t
            New-Item -ItemType Junction -Path ${'$'}p -Target '.' -ErrorAction Stop | Out-Null
            """.trimIndent(),
        )
    }

    fun remove(link: Path) {
        run(
            """
            ${pathVariable("p", link)}
            ${'$'}item = Get-Item -LiteralPath ${'$'}p -Force -ErrorAction Stop
            if (${'$'}item.LinkType -ne 'Junction') { throw 'Not a directory junction' }
            [IO.Directory]::Delete(${'$'}p)
            """.trimIndent(),
        )
    }

    // Paths are data inside an encoded command, never interpolated as PowerShell expressions.
    private fun pathVariable(
        variable: String,
        path: Path,
    ): String {
        val encoded = Base64.getEncoder().encodeToString(path.toString().toByteArray(Charsets.UTF_8))
        return "$$variable = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('$encoded'))"
    }

    private fun run(body: String): String {
        val script =
            """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}ProgressPreference = 'SilentlyContinue'
            [Console]::OutputEncoding = [Text.UTF8Encoding]::new(${'$'}false)
            try {
                $body
            } catch {
                [Console]::Error.WriteLine(${'$'}_.Exception.Message)
                exit 1
            }
            """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val process =
            ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
                .redirectErrorStream(true)
                .start()
        val output = CompletableFuture.supplyAsync { process.inputStream.use { it.readBytes().toString(Charsets.UTF_8) } }
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw IOException("Junction operation timed out")
            val text = output.get(5, TimeUnit.SECONDS).trim()
            if (process.exitValue() != 0) throw IOException(text)
            return text
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(error)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
