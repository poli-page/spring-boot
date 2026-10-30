plugins {
    id("org.springframework.boot") version "4.1.2"
}

description = "Example MVC Spring Boot app demonstrating every Poli Page SDK method via the starter"

dependencies {
    implementation(project(":poli-page-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":poli-page-spring-boot-autoconfigure")))
}

// The Boot plugin imports the BOM of its own version (above); import the build's springBootVersion
// again after it, so the app follows CI's Spring Boot matrix like the starters do.
extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${rootProject.properties["springBootVersion"]}")
    }
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
    archiveBaseName.set("poli-page-example-mvc")
    mainClass.set("page.poli.example.mvc.ExampleApplication")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    mainClass.set("page.poli.example.mvc.ExampleApplication")
    // POLI_PAGE_API_KEY is read from the environment by default; uncomment to override:
    // environment("POLI_PAGE_API_KEY", "pp_sandbox_…")
}

// Example app is not published.
tasks.named("javadocJar") { enabled = false }
tasks.named("sourcesJar") { enabled = false }
