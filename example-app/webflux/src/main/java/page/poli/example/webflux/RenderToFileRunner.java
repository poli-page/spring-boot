package page.poli.example.webflux;

import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import page.poli.sdk.PoliPageClient;
import page.poli.sdk.input.ProjectModeInput;

/** Demo step 3 — reactive variant uses {@code renderToFileAsync}, awaited on startup. */
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
    Path path = Path.of(args.getOptionValues("render-to-file").get(0));
    client
        .renderToFileAsync(
            ProjectModeInput.builder()
                .project("getting-started")
                .template("welcome")
                .version("1.0.0")
                .data(Map.of("name", "renderToFile demo (reactive)"))
                .build(),
            path)
        .join();
    log.info("Wrote PDF to {}", path.toAbsolutePath());
  }
}
