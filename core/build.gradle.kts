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

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // The test of the translations reads the sources of every module, so a change there has to run it again
    inputs.dir(rootProject.file("layout-editor/src/main")).withPropertyName("layoutEditorSources")
    inputs.dir(rootProject.file("app/src/main")).withPropertyName("appSources")
}

// Library module — disable the run task added by the compose plugin
tasks.matching { it.name == "run" }.configureEach {
    enabled = false
    group = ""
}

// Command line access to the EDDS codec, used by the test stand: ./gradlew :core:eddsTool --args="verify ..."
tasks.register<JavaExec>("eddsTool") {
    group = "verification"
    description = "Runs the EDDS command line tool"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.github.demandss.dayz_workbench_tool.texture.EddsCliKt")
}
