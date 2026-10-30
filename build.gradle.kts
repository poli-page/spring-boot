plugins {
    id("com.diffplug.spotless") version "6.25.0" apply false
}

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withJavadocJar()
        withSourcesJar()
    }

    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${rootProject.properties["springBootVersion"]}")
        }
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat("1.22.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
            target("src/**/*.java")
        }
        kotlinGradle {
            ktlint("1.3.1")
            target("*.gradle.kts", "**/*.gradle.kts")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = false
        }
    }
}

// Published to Maven Central: the two starters and the module they share. The example apps are not.
val publishedModules =
    mapOf(
        "poli-page-spring-boot-autoconfigure" to "Poli Page Spring Boot auto-configuration",
        "poli-page-spring-boot-starter" to "Poli Page Spring Boot starter (MVC)",
        "poli-page-spring-boot-starter-webflux" to "Poli Page Spring Boot starter (WebFlux)",
    )

// The modules are written here in Maven repository layout, then zipped into the bundle that the
// release workflow uploads to the Central Portal.
val centralStaging = layout.buildDirectory.dir("central-staging")

val cleanCentralStaging by tasks.registering(Delete::class) {
    delete(centralStaging)
}

configure(subprojects.filter { it.name in publishedModules }) {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    // The io.spring.dependency-management plugin would import the build's Spring Boot BOM into the
    // published POM. The POM lists resolved versions instead (versionMapping below), and the
    // application's own Spring Boot dependency management picks the versions it runs.
    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        generatedPomCustomization { enabled(false) }
    }

    // The test fixtures (stubbed API, contract runner) are for this repository's tests only.
    plugins.withId("java-test-fixtures") {
        val java = components["java"] as AdhocComponentWithVariants
        configurations
            .matching { it.name.startsWith("testFixtures") && it.isCanBeConsumed }
            .all { java.withVariantsFromConfiguration(this) { skip() } }
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                versionMapping {
                    usage("java-api") { fromResolutionOf("runtimeClasspath") }
                    usage("java-runtime") { fromResolutionResult() }
                }
                pom {
                    name.set(publishedModules.getValue(project.name))
                    description.set(provider { project.description })
                    url.set("https://github.com/poli-page/spring-boot")
                    inceptionYear.set("2026")
                    organization {
                        name.set("Poli Page")
                        url.set("https://poli.page")
                    }
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("poli-page")
                            name.set("Poli Page")
                            email.set("contact@poli.page")
                            organization.set("Poli Page")
                            organizationUrl.set("https://poli.page")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/poli-page/spring-boot.git")
                        developerConnection.set("scm:git:git@github.com:poli-page/spring-boot.git")
                        url.set("https://github.com/poli-page/spring-boot")
                        tag.set("v${project.version}")
                    }
                    issueManagement {
                        system.set("GitHub")
                        url.set("https://github.com/poli-page/spring-boot/issues")
                    }
                    ciManagement {
                        system.set("GitHub Actions")
                        url.set("https://github.com/poli-page/spring-boot/actions")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "centralStaging"
                url = uri(centralStaging)
            }
        }
    }

    // Signs only when the release workflow passes the key (ORG_GRADLE_PROJECT_signingKey and
    // ORG_GRADLE_PROJECT_signingPassword). Local builds and the verify job build unsigned.
    extensions.configure<SigningExtension> {
        val signingKey = providers.gradleProperty("signingKey")
        if (signingKey.isPresent) {
            useInMemoryPgpKeys(signingKey.get(), providers.gradleProperty("signingPassword").orNull)
            sign(extensions.getByType<PublishingExtension>().publications["maven"])
        }
    }

    tasks.named("publishMavenPublicationToCentralStagingRepository") {
        dependsOn(cleanCentralStaging)
    }
}

// The Central Portal bundle: build/central/central-bundle.zip. Maven Central computes the
// maven-metadata.xml files itself, so they stay out.
val centralBundle by tasks.registering(Zip::class) {
    group = "publishing"
    description = "Builds the Maven Central bundle of the published modules."
    dependsOn(publishedModules.keys.map { ":$it:publishMavenPublicationToCentralStagingRepository" })
    from(centralStaging) {
        exclude("**/maven-metadata.xml*")
    }
    destinationDirectory.set(layout.buildDirectory.dir("central"))
    archiveFileName.set("central-bundle.zip")
}

// Apply the io.spring.dependency-management plugin to all subprojects via buildscript classpath.
buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath("io.spring.gradle:dependency-management-plugin:${rootProject.properties["springDependencyManagementVersion"]}")
    }
}
