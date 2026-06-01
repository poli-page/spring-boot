plugins {
    id("org.springframework.boot") version "3.4.1"
}

description = "Example WebFlux Spring Boot app mirroring the MVC sibling, returning Mono / Flux types"

dependencies {
    implementation(project(":poli-page-spring-boot-starter-webflux"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("poli-page-example-webflux")
    mainClass.set("page.poli.example.webflux.ExampleApplication")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    mainClass.set("page.poli.example.webflux.ExampleApplication")
}

tasks.named("javadocJar") { enabled = false }
tasks.named("sourcesJar") { enabled = false }
