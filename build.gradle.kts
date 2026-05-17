plugins {
    kotlin("jvm") version "2.1.21" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    kotlin("plugin.serialization") version "2.1.21" apply false
}

group = "su.demands"
version = "1.0-SNAPSHOT"

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