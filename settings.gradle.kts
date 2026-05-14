rootProject.name = "Dayz Workbench Tool"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

include(":core", ":app", ":layout-editor")