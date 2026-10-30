# Releasing

Releases are **tag-driven** and publish to Maven Central through the Sonatype Central Portal ([`release.yml`](.github/workflows/release.yml)). A tag alone never publishes: the publish job waits for a maintainer to approve it on the `maven-central-release` environment. Central releases are immutable: a version can never be deleted or replaced.

Each release publishes three artifacts, all at the same version, in one Central deployment:

| Artifact                                          | What it is                                     |
| ------------------------------------------------- | ---------------------------------------------- |
| `page.poli:poli-page-spring-boot-starter`         | The MVC starter                                |
| `page.poli:poli-page-spring-boot-starter-webflux` | The WebFlux starter                            |
| `page.poli:poli-page-spring-boot-autoconfigure`   | The auto-configuration both starters depend on |

Each comes with its sources and javadoc jars, POM, Gradle module file, checksums and signatures. The example apps and the autoconfigure module's test fixtures are not published. The version of record is `version` in `gradle.properties`; the starters' version follows the `page.poli:sdk` version they require.

## One-time setup (before the first release)

On 2026-10-30 none of the three artifact names existed on Maven Central (`https://repo1.maven.org/maven2/page/poli/<artifact>/maven-metadata.xml` answered 404), and the `page.poli` namespace is already verified on the Central Portal, since `page.poli:sdk` is published there. So no namespace request is needed: the account that owns `page.poli` publishes these too.

1. **Central Portal user token.** Sign in to [central.sonatype.com](https://central.sonatype.com) with the account that owns the `page.poli` namespace, open **View Account → Generate User Token**, and keep the token's username and password. The token sdk-java already uses works too.
2. **Environment.** In `poli-page/spring-boot`, **Settings → Environments → New environment**, named exactly `maven-central-release`:
   - **Required reviewers:** the release owner.
   - **Deployment branches and tags:** **Selected branches and tags**, add a tag rule `v*`.
3. **Environment secrets** on `maven-central-release` (not repository secrets, so only the approved publish job can read them):
   - `CENTRAL_USERNAME`: the user token's username.
   - `CENTRAL_PASSWORD`: the user token's password.
   - `GPG_PRIVATE_KEY`: the ASCII-armored secret key of the release signing key, `gpg --armor --export-secret-keys 2DBB20D4D79D527DE02B8C3635C61308FA37BB15`. It is the key sdk-java signs with, published on keys.openpgp.org; its primary key signs (`[SC]`, no subkeys), which is the key the build signs with.
   - `GPG_PASSPHRASE`: that key's passphrase.

## Releasing a version

1. Set `version` in `gradle.properties` (no `-SNAPSHOT`).
2. Move `[Unreleased]` to `## [X.Y.Z] - YYYY-MM-DD` in `CHANGELOG.md`, leave an empty `## [Unreleased]` above it, and update the compare links at the bottom.
3. Update the version in the README install snippets, the README compatibility table and `docs/webflux.md`.
4. Merge `chore: release X.Y.Z` to `main` and wait for CI to go green.
5. Optional local check, which uploads nothing: build the bundle and check its contents.
   ```bash
   ./gradlew --no-daemon centralBundle
   .github/scripts/check-central-bundle.sh build/central/central-bundle.zip X.Y.Z unsigned
   ```
   To build it signed, pass the key as `ORG_GRADLE_PROJECT_signingKey` and `ORG_GRADLE_PROJECT_signingPassword`, then check it with `signed`.
6. Tag the merge commit and push the tag: `git tag vX.Y.Z <sha> && git push origin vX.Y.Z`.
7. The `verify` job checks that the tag matches `gradle.properties` and that `CHANGELOG.md` has the `## [X.Y.Z]` section, runs `spotlessCheck` and the full build with the pinned API contract (`POLI_CONTRACT`), then builds the unsigned Central bundle and checks it holds the three artifacts and nothing else.
8. Approve the `publish` job in the Actions UI. It builds and checks the signed bundle, uploads it to the Central Portal with automatic publishing, waits until the deployment is `PUBLISHED`, then creates the GitHub Release from the `CHANGELOG.md` section. If a later step fails after `PUBLISHED`, wait until `https://repo1.maven.org/maven2/page/poli/poli-page-spring-boot-starter/X.Y.Z/` shows the version (10–30 minutes) before re-running: the re-run then skips the upload and only creates the missing Release. A re-run before that uploads again, and Central fails the duplicate without changing what was published.
9. Check `https://repo1.maven.org/maven2/page/poli/poli-page-spring-boot-starter/X.Y.Z/` (Central can take 10–30 minutes to sync after `PUBLISHED`).

A failed deployment shows its errors in the job log and in the Central Portal's **Deployments** page. Nothing is published then, so fix the cause, merge, and release under a new tag; a published version can never be replaced.

### Prereleases

A version with a suffix (`1.0.0-RC1`, Maven's convention: uppercase, no dot) is a prerelease: build tools resolve it only when pinned explicitly, and the workflow marks its GitHub Release as a prerelease. Stable and prerelease versions never share a tag.
