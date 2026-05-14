plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
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