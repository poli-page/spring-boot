plugins {
    id("org.springframework.boot") version "3.4.1"
}

description = "Example WebFlux Spring Boot app mirroring the MVC sibling, returning Mono / Flux types"

dependencies {
    implementation(project(":poli-page-spring-boot-starter-webflux"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":poli-page-spring-boot-autoconfigure")))
}

// The contract tests (ContractTest) read the file at POLI_CONTRACT, and fail without it when CI
// is set. Both are inputs, so a cached result never stands in for a run against another contract.
tasks.named<Test>("test") {
    val contract = providers.environmentVariable("POLI_CONTRACT")
    val ci = providers.environmentVariable("CI").map { it.isNotBlank() }.orElse(false)
    inputs.property("ci", ci)
    inputs.property("contractSet", contract.map { it.isNotBlank() }.orElse(false))
    inputs
        .files(contract.map { listOf(it) }.orElse(listOf()))
        .withPropertyName("contract")
        .withPathSensitivity(PathSensitivity.NONE)
    doFirst {
        if (contract.orNull.isNullOrBlank() && !ci.get()) {
            logger.lifecycle("POLI_CONTRACT is not set: ContractTest is skipped. Run it with POLI_CONTRACT=<path to contract.json>.")
        }
    }
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
