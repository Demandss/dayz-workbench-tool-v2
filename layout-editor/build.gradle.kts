import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(21)
}

group = "com.github.demandss"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

// Move the compose run task out of the default group so IntelliJ doesn't
// confuse it with :app:run when clicking Run on app/Main.kt
tasks.matching { it.name == "run" }.configureEach {
    group = "layout-editor"
}

compose.desktop {
    application {
        mainClass = "com.github.demandss.dayz_workbench_tool.layout_editor.MainKt"

        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        .get()
        .metadata
        .installationPath
        .asFile
        .absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "DayZ Layout Editor"
            packageVersion = "1.0.0"
            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.management",
                "java.naming",
                "java.net.http",
                "jdk.unsupported",
            )
            windows {
                iconFile.set(project.file("src/main/resources/assets/logo/logo.ico"))
            }
        }
    }
}
