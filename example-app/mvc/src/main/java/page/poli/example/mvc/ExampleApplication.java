package page.poli.example.mvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable example MVC app demonstrating every Poli Page SDK method through the starter.
 *
 * <p>Run with: {@code POLI_PAGE_API_KEY=pp_test_… ./gradlew :example-app:mvc:bootRun}, then open <a
 * href="http://localhost:8080">http://localhost:8080</a>.
 */
@SpringBootApplication
public class ExampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(ExampleApplication.class, args);
  }
}
