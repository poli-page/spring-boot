description = "Poli Page Spring Boot starter (MVC) — autowires PoliPageClient and ResponseEntity helpers"

dependencies {
    api(project(":poli-page-spring-boot-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
