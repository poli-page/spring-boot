pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "poli-page-spring-boot"

include(
    "poli-page-spring-boot-autoconfigure",
    "poli-page-spring-boot-starter",
    "poli-page-spring-boot-starter-webflux",
    "example-app:mvc",
    "example-app:webflux",
)

