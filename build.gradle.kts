import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
}

kotlin {
    jvmToolchain(21)
}

group = "su.demands"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

compose.desktop {
    application {
        mainClass = "su.demands.dayz_workbench_tool.MainKt"

        javaHome = javaToolchains
            .launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
            .get()
            .metadata
            .installationPath
            .asFile
            .absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "Dayz Workbench Tool"
            packageVersion = "1.0.0"
            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.management",
                "java.naming",
                "java.net.http",
                "jdk.unsupported"
            )
            windows {
                iconFile.set(project.file("src/main/resources/assets/logo/logo.ico"))
            }
        }
    }
}

tasks.register<Zip>("packagePortable") {
    group = "compose desktop"
    description = "Packages the release distributable into a portable zip"
    dependsOn("createReleaseDistributable")
    archiveFileName.set("DayzWorkbenchTool.zip")
    destinationDirectory.set(layout.buildDirectory.dir("release"))
    from(layout.buildDirectory.dir("compose/binaries/main-release/app"))
}