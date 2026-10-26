plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(25)
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
    api(compose.components.resources)
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation(kotlin("test"))
    // The tests of what reads the string resources need the native library of the graphics, like the programs
    testImplementation(compose.desktop.currentOs)
}

// The strings of the interface for every module: `Res.string.*` of this package, in src/main/composeResources
compose.resources {
    publicResClass = true
    packageOfResClass = "com.github.demandss.dayz_workbench_tool.resources"
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-XX:ErrorFile=logs/hs_err_pid%p.log",
        "-XX:ReplayDataFile=logs/replay_pid%p.log",
        "--enable-native-access=ALL-UNNAMED",
    )
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
