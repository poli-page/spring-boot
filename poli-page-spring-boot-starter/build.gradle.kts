description = "Poli Page Spring Boot starter (MVC) — autowires PoliPageClient and ResponseEntity helpers"

dependencies {
    api(project(":poli-page-spring-boot-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter-web")
    // The MVC error handler uses the servlet API, whichever container provides it at runtime.
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":poli-page-spring-boot-autoconfigure")))
}
