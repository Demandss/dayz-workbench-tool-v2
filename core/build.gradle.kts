plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    api(compose.material3)
    api(compose.foundation)
    api(compose.ui)
    api(compose.runtime)
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

// Library module — disable the run task added by the compose plugin
tasks.matching { it.name == "run" }.configureEach {
    enabled = false
    group = ""
}
