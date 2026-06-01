plugins {
    id("org.springframework.boot") version "3.4.1"
}

description = "Example MVC Spring Boot app demonstrating every Poli Page SDK method via the starter"

dependencies {
    implementation(project(":poli-page-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("poli-page-example-mvc")
    mainClass.set("page.poli.example.mvc.ExampleApplication")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    mainClass.set("page.poli.example.mvc.ExampleApplication")
    // POLI_PAGE_API_KEY is read from the environment by default; uncomment to override:
    // environment("POLI_PAGE_API_KEY", "pp_test_…")
}

// Example app is not published.
tasks.named("javadocJar") { enabled = false }
tasks.named("sourcesJar") { enabled = false }
