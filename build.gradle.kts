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

// The version of the programs. The release workflow writes the version of a release into the next line; the modules read it from here.
version = "1.0.0"


val ktlintCheckAll by tasks.registering {
    group = "verification"
    description = "Run ktlint checks across all subprojects"
    dependsOn(subprojects.map { ":${it.name}:ktlintCheck" })
}

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

    afterEvaluate {
        tasks.matching { it.name in setOf("run", "createDistributable", "createReleaseDistributable") }
            .configureEach { dependsOn(ktlintCheckAll) }
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