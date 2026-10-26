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
version = rootProject.version

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
}

compose.desktop {
    application {
        mainClass = "com.github.demandss.dayz_workbench_tool.layout_editor.MainKt"

        // Crash dumps go to logs/ (ignored by git) instead of the working directory
        jvmArgs += listOf("-XX:ErrorFile=logs/hs_err_pid%p.log", "-XX:ReplayDataFile=logs/replay_pid%p.log")

        javaHome =
            javaToolchains
                .launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(21))
                }.get()
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

dependencies {
    testImplementation(kotlin("test"))
}

// The version goes into app-version.properties, for the server of the editor to tell its clients (see McpHost)
tasks.processResources {
    val appVersion = project.version.toString()
    inputs.property("appVersion", appVersion)
    filesMatching("app-version.properties") { expand("version" to appVersion) }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-XX:ErrorFile=logs/hs_err_pid%p.log", "-XX:ReplayDataFile=logs/replay_pid%p.log")
}
