package page.poli.example.mvc;

import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.input.ProjectModeInput;

/**
 * Demo step 3 — invokes {@link PoliPageClient#renderToFile(ProjectModeInput, Path)} from a Spring
 * {@link ApplicationRunner} when the app is launched with {@code --render-to-file=PATH}.
 *
 * <p>Example: {@code ./gradlew :example-app:mvc:bootRun --args='--render-to-file=./welcome.pdf'}.
 *
 * <p>Inert by default — does nothing when the option is absent, so the normal {@code bootRun}
 * starts the web server without writing a file.
 */
@Component
public class RenderToFileRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(RenderToFileRunner.class);

  private final PoliPageClient client;

  public RenderToFileRunner(PoliPageClient client) {
    this.client = client;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!args.containsOption("render-to-file")) {
      return;
    }
    String target = args.getOptionValues("render-to-file").get(0);
    Path path = Path.of(target);
    client.renderToFile(
        ProjectModeInput.builder()
            .project("getting-started")
            .template("welcome")
            .version("1.0.0")
            .data(Map.of("name", "renderToFile demo"))
            .build(),
        path);
    log.info("Wrote PDF to {}", path.toAbsolutePath());
  }
}
