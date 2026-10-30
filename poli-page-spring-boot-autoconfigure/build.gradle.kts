plugins {
    `java-test-fixtures`
}

description = "Common auto-configuration for the Poli Page Spring Boot starter (MVC + WebFlux share this)"

dependencies {
    api("page.poli:sdk:0.11.0")
    api("org.springframework.boot:spring-boot-autoconfigure")

    // The error handler; both starters bring spring-web.
    compileOnly("org.springframework:spring-web")

    // Optional dependencies — beans are @ConditionalOnClass so consumers without these still get a working starter.
    compileOnly("org.springframework.boot:spring-boot-actuator")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("org.hibernate.validator:hibernate-validator")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("io.micrometer:micrometer-core")

    // Shared with the example apps' tests: the test keys and the stubbed API.
    testFixturesApi("org.wiremock:wiremock-standalone:3.9.1")
}
