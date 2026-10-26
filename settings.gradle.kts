rootProject.name = "Dayz Workbench Tool"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":core", ":app", ":layout-editor")