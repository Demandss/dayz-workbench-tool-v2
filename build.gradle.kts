import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("jvm") version "2.1.21" apply false

    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false

    id("org.jetbrains.compose") version "1.8.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false

    kotlin("plugin.serialization") version "2.1.21" apply false
}

group = "com.github.demandss"
version = "1.0-SNAPSHOT"


subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<KtlintExtension> {
        version.set("1.8.0")

        verbose.set(true)
        outputToConsole.set(true)
        coloredOutput.set(true)

        reporters {
            reporter(ReporterType.PLAIN)
            reporter(ReporterType.CHECKSTYLE)
        }
    }
}

tasks.register("createReleaseDistributable") {
    group = "compose desktop"
    description = "Builds the distributable and copies it to the root build/compose directory"
    dependsOn(":app:createReleaseDistributable")

    doLast {
        val src = project(":app").layout.buildDirectory
            .dir("compose/binaries/main-release/app/Dayz Workbench Tool").get().asFile
        val dst = layout.buildDirectory
            .dir("compose/binaries/main-release/app/Dayz Workbench Tool").get().asFile

        dst.deleteRecursively()
        src.copyRecursively(dst)
    }
}