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
)

// SDK dev-override note (Why: sdk-java is a Maven project, so Gradle's includeBuild
// is not usable as a composite. To test against unreleased SDK changes, run
// `./mvnw install -DskipTests` inside ../sdk-java/, which publishes the artifact to
// ~/.m2/repository; the root build.gradle.kts lists `mavenLocal()` first so Gradle
// resolves it before Maven Central.
