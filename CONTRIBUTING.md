# Contributing to Poli Page for Spring Boot

Thanks for your interest in improving this starter. This guide covers setup, tests, and conventions.

## Setup

Install the local SDK snapshot once (until `page.poli:sdk` ships to Maven Central):

```bash
cd ../sdk-java && ./mvnw install -DskipTests
```

Set `POLI_PAGE_API_KEY` in your environment (a `pp_test_*` key is fine) before running integration tests:

```bash
export POLI_PAGE_API_KEY=pp_test_…
```

## Tests

Unit tests across all modules:

```bash
./gradlew --no-daemon test
```

Per-module:

```bash
./gradlew --no-daemon :poli-page-spring-boot-autoconfigure:test
./gradlew --no-daemon :poli-page-spring-boot-starter:test
./gradlew --no-daemon :poli-page-spring-boot-starter-webflux:test
```

Integration tests are skipped automatically when `POLI_PAGE_API_KEY` is unset, so PR contributors without a key still get a green local run.

## Lint, format, build

```bash
./gradlew --no-daemon spotlessCheck    # google-java-format compliance
./gradlew --no-daemon spotlessApply    # auto-format
./gradlew --no-daemon build            # spotless + compile + test + assemble
```

CI matrix: Java 17 / 21 × Spring Boot 3.2.+ / 3.3.+ / 3.4.+ (6 cells). All must be green before merge.

## Pull requests

- [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`.
- One concern per PR, reviewable in under 30 minutes.
- PR description: what changed, why, how it was tested.
- CI must be green on all 6 matrix cells before merge.

## Reporting issues

Open an issue at [github.com/poli-page/spring-boot/issues](https://github.com/poli-page/spring-boot/issues) with:

- The Spring Boot version and Java version you're on
- A minimal reproducer (a snippet of `application.yml` + a stripped-down controller is usually enough)
- For runtime failures, the relevant stack trace and the `requestId` from the `PoliPageException` if one was thrown

For security-sensitive reports, email security@poli.page instead of opening a public issue.

## License

By contributing, you agree your contributions will be licensed under the [MIT License](LICENSE).
