description = "Poli Page Spring Boot starter (WebFlux) — autowires PoliPageClient and reactive response helpers"

dependencies {
    api(project(":poli-page-spring-boot-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter-webflux")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}
