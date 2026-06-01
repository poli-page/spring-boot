package page.poli.example.webflux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable example WebFlux app mirroring the MVC sibling — same 10 SDK demo steps, reactive return
 * types ({@code Mono} / {@code Flux}).
 *
 * <p>Run with: {@code POLI_PAGE_API_KEY=pp_test_… ./gradlew :example-app:webflux:bootRun}, then
 * open <a href="http://localhost:8081">http://localhost:8081</a>.
 */
@SpringBootApplication
public class ExampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(ExampleApplication.class, args);
  }
}
