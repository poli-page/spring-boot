# Configuration properties

> Every property the starter reads from `application.yml` / `application.properties` / environment variables — what it binds to, how it's validated, and what happens when you leave it unset.

## Why

The starter is configured exclusively through Spring Boot's `@ConfigurationProperties` mechanism. There's no `init()` method to call, no static `PoliPage.configure(...)` to invoke, no Java-side builder you have to construct. You set `poli-page.api-key` and the autoconfig produces a `PoliPageClient` bean. Anything else you set tunes that bean; anything you leave unset falls through to the SDK's own defaults — meaning a future SDK release that bumps the default `requestTimeout` from 60 s to 90 s takes effect for your app without a starter upgrade.

The properties record is validated at startup via Jakarta Bean Validation. Bad input — a malformed `api-key`, a zero-duration `requestTimeout`, a 12-retry `maxAttempts` — fails the boot with a clear `BindValidationException`, not at first call.

## How

### The full property surface

```yaml
poli-page:
  api-key: ${POLI_PAGE_API_KEY}                # REQUIRED — must match pp_(test|live)_.+
  base-url:                                    # optional URI, e.g. https://api.poli.page
  request-timeout:                             # optional Duration, e.g. 30s, PT1M
  retries:
    max-attempts:                              # optional integer in [0, 10]
    delay:                                     # optional Duration in [0s, 30s]
  health:
    enabled: true                              # default true — toggles HealthIndicator
  metrics:
    enabled: true                              # default true — toggles MeterBinder
  events:
    enabled: true                              # default true — toggles ApplicationEvent bridge
```

Bound to the `PoliPageProperties` record:

```java
@ConfigurationProperties("poli-page")
@Validated
public record PoliPageProperties(
    @NotBlank @Pattern(regexp = "^pp_(test|live)_.+$",
        message = "Poli Page API key must start with pp_test_ or pp_live_. " +
                  "Get one at https://poli.page/dashboard/keys.")
    String apiKey,
    @Nullable URI baseUrl,
    @Nullable @DurationMin(seconds = 1) @DurationMax(seconds = 600) Duration requestTimeout,
    @NotNull Retries retries,
    @NotNull Health health,
    @NotNull Metrics metrics,
    @NotNull Events events
) {
    public record Retries(
        @Nullable @Min(0) @Max(10) Integer maxAttempts,
        @Nullable @DurationMin(millis = 0) @DurationMax(seconds = 30) Duration delay
    ) {}
    public record Health(boolean enabled) {}
    public record Metrics(boolean enabled) {}
    public record Events(boolean enabled) {}
}
```

### Per-property reference

| Property | Type | Required | Default | Notes |
|---|---|---|---|---|
| `poli-page.api-key` | `String` | yes | — | Must match `pp_test_` or `pp_live_`. Failed validation surfaces at startup. |
| `poli-page.base-url` | `URI` | no | SDK default (`https://api.poli.page`) | Must be absolute. Used for staging / self-hosted deployments. |
| `poli-page.request-timeout` | `Duration` | no | SDK default (60 s) | Per-attempt request timeout. Spring Boot relaxed binding accepts `30s`, `1m`, `PT45S`. |
| `poli-page.retries.max-attempts` | `Integer` | no | SDK default (2) | Number of retries on top of the initial request. `0` disables retries. |
| `poli-page.retries.delay` | `Duration` | no | SDK default (500 ms) | Base delay for exponential backoff. Jitter and `Retry-After` capping happen inside the SDK. |
| `poli-page.health.enabled` | `boolean` | no | `true` | Set to `false` to skip registering the `HealthIndicator`. |
| `poli-page.metrics.enabled` | `boolean` | no | `true` | Set to `false` to skip Micrometer meters. |
| `poli-page.events.enabled` | `boolean` | no | `true` | Set to `false` to drop the `ApplicationEventPublisher` bridge. |

### Relaxed binding & env-var mapping

Spring Boot's relaxed binding lets the same property surface as several spellings:

- `POLI_PAGE_API_KEY` (env var) → `poli-page.api-key` (yaml) → `poliPage.apiKey` (java property).
- `POLI_PAGE_RETRIES_MAX_ATTEMPTS` → `poli-page.retries.max-attempts` → `poliPage.retries.maxAttempts`.

You don't pick one — use whichever is idiomatic for the source. Deployment platforms (Heroku, Cloud Run, ECS) typically inject env vars; `application.yml` lives in source.

### A minimal `application.yml`

```yaml
poli-page:
  api-key: ${POLI_PAGE_API_KEY}
```

That's all you need. With this file and a `POLI_PAGE_API_KEY=pp_test_...` in the environment, the autoconfig produces a fully-functional `PoliPageClient` bean.

### A staging-vs-production split

```yaml
# application.yml (defaults)
poli-page:
  api-key: ${POLI_PAGE_API_KEY}

# application-staging.yml (activated by SPRING_PROFILES_ACTIVE=staging)
poli-page:
  base-url: https://api-staging.poli.page
  request-timeout: 30s

# application-prod.yml
poli-page:
  request-timeout: 90s
  retries:
    max-attempts: 4
```

Spring Boot's profile mechanism handles the merge — properties from `application-prod.yml` overlay the defaults.

## Validation behaviour

Bad properties surface as a `BindValidationException` at `SpringApplication.run(...)` time:

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Binding to target [Bindable@... type = page.poli.sdk.spring.PoliPageProperties] failed:

    Property: poli-page.api-key
    Value: "sk_live_dashboardtoken"
    Origin: System Environment Property "POLI_PAGE_API_KEY"
    Reason: Poli Page API key must start with pp_test_ or pp_live_. Get one at https://poli.page/dashboard/keys.

Action:

Update your application's configuration
```

This is the path that catches the #1 misconfiguration: pasting a dashboard token instead of an API key. The match is regex `^pp_(test|live)_.+$` — fail-loud, not fail-quiet.

Other validations:

- `request-timeout: 0s` → `DurationMin` rejection ("must be greater than or equal to PT1S").
- `request-timeout: 700s` → `DurationMax` rejection ("must be less than or equal to PT10M").
- `retries.max-attempts: -1` → `Min` rejection.
- `retries.max-attempts: 11` → `Max` rejection.

## IDE auto-completion

The starter ships `META-INF/spring-configuration-metadata.json` so IntelliJ IDEA, VS Code (with the Spring Tools extension), and Eclipse STS suggest `poli-page.*` keys as you type, with type hints, default values, and descriptions sourced from the record's Javadoc. No extra plugin needed — Spring Boot's tooling reads this file by convention.

```yaml
poli-page:
  api  # IDE shows: api-key (String, REQUIRED) — Your Poli Page API key. Find one at https://poli.page/dashboard/keys.
```

## Externalising secrets

`api-key` is sensitive — never commit it to a repository. Three options, in increasing operational sophistication:

### 1. Environment variable

```bash
export POLI_PAGE_API_KEY=pp_live_...
./gradlew bootRun
```

`application.yml` references it: `api-key: ${POLI_PAGE_API_KEY}`. The 12-factor default.

### 2. `.env` loaded by Spring Boot's profile

A `application-local.yml` not under version control:

```yaml
poli-page:
  api-key: pp_test_...
```

Activate with `SPRING_PROFILES_ACTIVE=local` during development. Add `application-local.yml` to `.gitignore`.

### 3. Spring Cloud Config / Vault / AWS Secrets Manager

For production, use the bootstrap configuration mechanism your platform already provides. Spring Cloud Vault, AWS Secrets Manager, GCP Secret Manager, HashiCorp Vault — any of them resolves `poli-page.api-key` from a remote secret store before binding happens.

## Gotchas

- **`base-url` must be absolute.** A relative URI like `/v1` is silently invalid; the autoconfig throws `IllegalArgumentException` from the SDK builder. Set the full origin: `https://api.poli.page` (no trailing path).
- **Boolean toggles default to `true`.** `poli-page.health.enabled`, `poli-page.metrics.enabled`, `poli-page.events.enabled` are all opt-out, not opt-in. Omitting them is equivalent to `true` — flip to `false` to disable the corresponding bean.
- **Relaxed binding case is environment-specific.** Linux env vars are case-sensitive (`POLI_PAGE_API_KEY` only); Windows is not. Stick with UPPER_SNAKE_CASE for env vars to keep platforms aligned.
- **`@DurationMin` / `@DurationMax` annotations are Spring Boot-specific.** They live in `org.springframework.boot.convert`. Don't reach for Jakarta Validation equivalents — they don't exist.
- **Properties aren't refreshable.** The autoconfig reads `PoliPageProperties` once at startup. Spring Cloud Config's `@RefreshScope` is not wired in v0.1 (deferred to a future release); changing `api-key` at runtime requires an application restart.

## Related

- [Auto-configuration](auto-configuration.md) — how the properties drive the bean wiring.
- [spec/spring-boot-starter-specification.md §6](spec/spring-boot-starter-specification.md#6-configuration-properties) — full property tree with rationale for each validation rule.
- [README → Quick start](../README.md#quick-start) — the minimal-config snippet this deep-dive expands.
