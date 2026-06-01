# Contributing to Poli Page Spring Boot Starter

Thanks for your interest in improving this starter. This guide covers setup, tests, and conventions.

## Setup

```bash
./gradlew --no-daemon build
```

The first run pulls Gradle 8.10, the Spring Boot BOM for the default `springBootVersion`, and the SDK from Maven Central (or from `../sdk-java/` if the composite-build override is active — see [§9 of CLAUDE.md](CLAUDE.md#9-sdk-dependency-note)).

Set `POLI_PAGE_API_KEY` in your environment (a `pp_test_*` key is fine) before running the integration test:

```bash
export POLI_PAGE_API_KEY=pp_test_...
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

Integration test (hits `api-develop.poli.page`, gated on `POLI_PAGE_API_KEY`):

```bash
./gradlew --no-daemon integrationTest
```

Integration tests are skipped when `POLI_PAGE_API_KEY` is unset. PRs from contributors without a key still get green local runs.

## Lint, format, type-check

```bash
./gradlew --no-daemon spotlessCheck    # google-java-format compliance
./gradlew --no-daemon spotlessApply    # auto-format
./gradlew --no-daemon check            # Spotless + Error Prone + NullAway + tests
```

The full CI suite:

```bash
./gradlew --no-daemon build check
```

## Code conventions

- **Java 17+ only.** Spring Boot 3 requires it; the SDK requires it. Don't introduce `--release 21` features that break the floor.
- **Spotless + google-java-format.** Run `spotlessApply` before pushing.
- **Error Prone + NullAway.** Build fails on warnings — treat them as errors locally.
- **JSpecify `@Nullable`** for nullable fields and parameters.
- **No `@SuppressWarnings`** without a `// Why:` comment explaining the constraint.
- **Default to no comments.** Public Javadoc is required (Maven Central enforces it). Comments restating *what* the code does are noise.
- **`@ConditionalOnMissingBean` on every starter-provided bean.** Always allow user override.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` new feature
- `fix:` bug fix
- `docs:` documentation only
- `chore:` build / tooling
- `refactor:` non-behavioural change
- `test:` test additions or refactors

One concern per PR, reviewable in under 30 minutes.

## CI

`.github/workflows/ci.yml` matrix: Java 17 / 21 × Spring Boot 3.2.+ / 3.3.+ / 3.4.+ (6 cells). All must be green before merge.

Local mirror:

```bash
./gradlew --no-daemon build spotlessCheck
```

## Release process

For maintainers — pushing a tagged release publishes to Maven Central.

1. Confirm `CHANGELOG.md` has an entry for the new version with the date filled in.
2. Bump `version` in `gradle.properties`.
3. `git commit -m "chore: release v0.x.y"` and push to `main`.
4. `git tag v0.x.y && git push --tags`.
5. The `.github/workflows/publish.yml` workflow:
   - Builds + signs all three artifacts (`-autoconfigure`, `-starter`, `-starter-webflux`)
   - Uploads to Sonatype OSSRH
   - Auto-promotes the staging repository via `nexus-publish-plugin`
6. Wait ~30 minutes – 4 hours for Central indexing.
7. Smoke-test: `curl -fsSL https://repo1.maven.org/maven2/page/poli/poli-page-spring-boot-starter/0.x.y/poli-page-spring-boot-starter-0.x.y.pom`.

Secrets needed in the GitHub repo:

- `OSSRH_USERNAME` / `OSSRH_PASSWORD` — Sonatype credentials
- `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` — signing key (shared with `sdk-java`)

## Questions / blockers

- Spec ambiguity: [`docs/spec/spring-boot-starter-specification.md`](docs/spec/spring-boot-starter-specification.md) is the authoritative source. If it's silent on something, open an issue.
- SDK behaviour: read `/Users/mickael/Projects/sdk-java/` first, then ask the maintainer.
- CI flakiness on `main`: open an issue tagged `ci-flake`; do not let it block your PR.

## License

By contributing, you agree your contributions will be licensed under the [MIT License](LICENSE).
