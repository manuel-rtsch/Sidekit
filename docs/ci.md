# Continuous integration

Sidekit runs its checks on GitHub Actions. All jobs are defined in
[`.github/workflows`](../.github/workflows) and share the JDK 21, Android SDK 37 and
Gradle cache setup in [`.github/actions/android-setup`](../.github/actions/android-setup).

## What runs

| Job | Command | Catches |
| --- | --- | --- |
| Gradle wrapper validation | `gradle/actions/wrapper-validation` | A modified or unverified `gradle-wrapper.jar` |
| Build | `./gradlew :app:assembleDebug :app:assembleRelease :sidekit:assembleDebug :sidekit-sdk:assembleDebug :sample-app:assembleDebug :permission-test-app:assembleDebug :permission-test-app:assembleDebugAndroidTest` | Compile errors and R8/keep-rule breakage that only release builds show |
| Published SDK consumer | publish to `build/pages-repo`, then `:sample-app:assembleDebug -PsidekitUsePublished=true` | Missing POM dependencies, broken transitive `sidekit-design` resolution, consumer rules and the artifact host itself |
| Publish SDK | tag `v*` or manual dispatch, then publish to GitHub Pages | Release artifacts and POM metadata, and a `maven-metadata.xml` that omits a published version |
| Release APK | tag `v*` or manual dispatch: `:app:assembleRelease`, signed with the repository keystore and attached to the release | A release APK that cannot install over the previous one |
| Unit tests | `./gradlew testDebugUnitTest` | JVM, Robolectric and Compose tests in every module |
| Android Lint | `./gradlew lintDebug` | API-level, manifest and resource problems; uploads SARIF to code scanning |
| Kotlin formatting | `./gradlew spotlessCheck` | ktlint violations in files changed since `main` |
| Secret scan | `gitleaks/gitleaks-action` | Committed credentials, private keys and tokens |
| Dependency review | `actions/dependency-review-action` | High-severity vulnerable dependencies in a pull request |
| CodeQL | manual Gradle build + `codeql-action` | Security data-flow issues in Kotlin and Java (`codeql.yml`) |
| Dependency graph | `gradle/actions/dependency-submission` | Keeps the graph that dependency review reads up to date (`dependency-graph.yml`) |

These checks gate pull requests. The repository does not run instrumented tests on an emulator:
the two real ones (`:sample-app` and `:permission-test-app`) need Sidekit installed beside them on
the same device to exercise the exported configuration provider and its cross-UID permission
boundary, and the [PR template](../.github/pull_request_template.md) already requires
physical-device testing for app changes. Run them by hand against an installed debug Sidekit:

```bash
./gradlew :app:installDebug
./gradlew :sample-app:connectedDebugAndroidTest :permission-test-app:connectedDebugAndroidTest
```

Add a `connectedDebugAndroidTest` job once an emulator setup that installs all three APKs exists.

## Publishing

`:sidekit` and `:sidekit-sdk` are publishable libraries. Version metadata comes from git tags
through the `sidekit.version` and `sidekit.publish` convention plugins in
[`build-logic`](../build-logic). The convention plugin configures a local staging repository at
`build/local-repo` for quick checks, and stages the published layout at `build/pages-repo` when
`-PsidekitPublishPages=true` is set.

```bash
./gradlew :sidekit:publishAllPublicationsToSidekitPagesRepository \
  :sidekit-sdk:publishAllPublicationsToSidekitPagesRepository -PsidekitPublishPages=true
./gradlew :sample-app:assembleDebug -PsidekitUsePublished=true
```

`:sample-app` uses the project dependency by default. The `Published SDK consumer` CI job stages
the Pages layout and rebuilds the sample with `-PsidekitUsePublished=true`, which exercises the
POM, the transitive `sidekit-design` dependency, the consumer rules and the exact tree that is
served to third-party apps (`settings.gradle.kts` lists `build/pages-repo` as a repository).

The `Publish SDK` workflow runs on `vX.Y.Z` tags (a manual run must also be started from such a
tag; it refuses branches and versions that are already published), stages the same tree and pushes it into
the `gh-pages` branch, which GitHub Pages serves as a static Maven repository. Nothing is
uploaded to a package registry, so consumers need no credentials:

```kotlin
repositories {
    google()
    mavenCentral()
    maven("https://<owner>.github.io/Sidekit/maven")
}

dependencies {
    implementation("hrln.interactive.sidekit:sidekit-sdk:<version>")
}
```

The workflow keeps older versions in `gh-pages` (consumers pin versions): it seeds `build/pages-repo`
from the published tree first, so each `maven-metadata.xml` gains the new version instead of being
replaced. It also writes `.nojekyll`, so
Pages serves the files untouched. Enable it once in the repository settings: **Settings → Pages →
Deploy from a branch → `gh-pages` / root**. The site URL is
`https://<owner>.github.io/Sidekit/maven/`.

Before pushing, the workflow fails the run if the staged metadata is not self-consistent: every
version directory on the branch has to appear in that artifact's `maven-metadata.xml`, the version
being published has to be listed, and `latest`/`release` have to name it. Gradle has shipped a
metadata file that listed only the last three versions while the 1.4.2–1.7.3 directories sat beside
it, and a resolver that trusts metadata cannot see an artifact it does not list. The check runs
against the staging tree, so a bad file never reaches `gh-pages`.

## The setup guide (`site/`)

`site/` is a separate, single-page install guide deployed to Vercel, not to GitHub Pages: Pages
serves only the Maven tree. Edit `site/index.html` there, and keep its download links on this
repository's releases — the guide describes the APKs this fork signs.


## Formatting

Formatting is checked with ktlint through Spotless. It is *ratcheted* against
`origin/main`, so only files changed on a branch must be clean. This lets the
project adopt formatting without rewriting untouched code.

```bash
./gradlew spotlessCheck   # report violations
./gradlew spotlessApply   # format the changed files
```

The rules live in [`.editorconfig`](../.editorconfig). `ktlint_code_style =
android_studio` matches Android Studio's defaults, `max_line_length = off` keeps the
existing long-line style, and `no-wildcard-imports` is disabled because a few MMS
and WorkManager adapters intentionally import whole packages. `@Composable`
functions are exempt from `function-naming`, because screens are PascalCase by
convention.

Spotless does not load `.editorconfig` on its own: `build.gradle.kts` reads the
ktlint keys from it and passes them as `editorConfigOverride`. Keep editing the
`.editorconfig` file; the build picks up new keys. ktlint caches the parsed rules
inside the Gradle daemon, so after editing it run `./gradlew --stop` before the
next `spotlessApply` or `spotlessCheck`.

CI checks out the full history so the `origin/main` ref that Spotless ratchets
against exists. Locally, fetch `main` first if the check complains about a missing
ref.

## Running the pipeline locally

```bash
./gradlew :app:assembleDebug :app:assembleRelease :sidekit:assembleDebug :sidekit-sdk:assembleDebug :sample-app:assembleDebug :permission-test-app:assembleDebug :permission-test-app:assembleDebugAndroidTest
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew spotlessCheck
```

Reports are written to `app/build/reports/`, `sidekit/build/reports/`, `sidekit-sdk/build/reports/`
and `sample-app/build/reports/`.

## Branch protection

Recommended settings for `main`:

- Require a pull request before merging.
- Require the status checks `Gradle wrapper validation`, `Build (debug + R8 release)`,
  `Unit tests`, `Android Lint`, `Kotlin formatting`, and `Secret scan`.
- Require branches to be up to date before merging.
- Do not allow bypassing the above settings.

`Dependency review` only runs on pull requests, and `CodeQL` runs on pushes and
pull requests to `main`; keep them optional if they are still being tuned.

## Things that are intentionally not in PR CI

- **Network integration probes** (`scripts/verify_nsw_transit.py`) depend on a
  third-party service and should run on a schedule or manually, not block a pull
  request.
- **Emulator tests**: see above.
- **Release signing**: it runs on tags in the `Release APK` workflow, not on pull
  requests. The `release` build type stays unsigned so a PR never needs a key,
  and CI only verifies that R8 succeeds.

## Tagging

A release *is* a tag: `sidekit.version` derives `versionName` from `git describe --tags --long` and
`versionCode` from the commit count, and both release workflows key off `v*`. A tag that is before
its `CHANGELOG.md` section, or not on `main`, or lightweight where the rest are annotated, produces
an artifact that does not trace back to a commit — which is the whole point of tagging.

Fetch tags before you build. A clone, or a `git fetch upstream` that never asked for tags, leaves
`git describe` anchored on an old release and the version lies: this repository reported
`1.7.0-127-g8dce85c` until `git fetch --tags origin` brought `v1.7.1`–`v1.10.1` in.

The convention, for every release:

1. **Promote the CHANGELOG section in the tagged commit.** `## Unreleased` becomes
   `## vX.Y.Z — <date>`. The `Release APK` workflow creates the GitHub release with placeholder
   notes on purpose, and the notes are edited afterwards from that section.
2. **Annotated tags only** (`git tag -a`). The history is mixed — `v1.0`, `v1.3.0`, `v1.5.0`,
   `v1.7.2`, `v1.7.3`, `v1.9.1` and `v1.9.2` are lightweight — and that is not worth rewriting, but
   an annotated tag carries the tagger, date and message that a bare ref does not.
3. **Signing is supported and not yet used.** `git config tag.gpgSign true`, or `gpg.format=ssh`
   with an SSH key, applies to all later tags; no tag so far is signed.
4. **`vX.Y.Z` only.** `release-apk.yml` rejects anything else, and `publish-sdk.yml` publishes the
   tag verbatim as the Maven version, so a `-rc` stamp would become a permanent library version.
   Stage pre-releases in `## Unreleased` instead.
5. **Tag a commit on `main`.** `v1.7.3` is the one exception and stays as it is: it points at
   `a73addf`, a pre-rebase copy of `c57ec95` (*feat(header): add a touch-mode Options button*),
   which is the commit on `main` that `v1.8.0` follows. Re-pointing it would contradict the APK and
   the `sidekit-sdk:1.7.3` artifacts already published from it, and `v1.8.0` ships the same change.
   Any other tag has to pass `git merge-base --is-ancestor <tag> main`.
6. **Then push the tag**: `git push origin vX.Y.Z`. `Release APK` waits for the *release*
   environment approval, `Publish SDK` pushes to `gh-pages`, and the `site/` download links and the
   `gh-pages` *Publish vX.Y.Z* commit carry the same version.

Check before pushing:

```bash
git describe --tags --long --dirty      # vX.Y.Z-0-g<sha>, with no -dirty suffix
git rev-list --count HEAD               # versionCode: must exceed the previous tag's count
```

`git rev-list --count <tag>` gives the `versionCode` of a past release, so a release that skipped a
number is visible without installing the APK.

## Release signing

The signature on an APK is its identity to Android: only an APK signed with the same key can install
as an update over an installed copy. v1.3.0–v1.6.0 were signed with the project's release key, but
v1.9.1 shipped signed with a local debug keystore, so it could not be upgraded at all — no other
build can ever reproduce that certificate. The `Release APK` workflow exists so that cannot happen
again by hand.

It runs on every `vX.Y.Z` tag:

1. checks the run started from a release tag, and checks out the full history so `versionName` and
   `versionCode` (the commit count) stay honest;
2. decodes the keystore from secrets into `$RUNNER_TEMP`, never into the workspace;
3. builds `:app:assembleRelease`, which stays unsigned so the build itself needs no secrets;
4. signs with `apksigner` (v2/v3) and **fails if the certificate is not the expected one**;
5. writes `SHA256SUMS.txt`, creates the release for the tag if it does not exist, and uploads
   `Sidekit-<version>.apk`, `Sidekit-<version>-release-unsigned.apk` and `SHA256SUMS.txt`.

A tag push therefore stops at the signing job until the owner approves the deployment in the
**Actions** run. That approval is the only thing standing between the secrets and a run nobody
asked for, so treat a pending *release* environment request as a signal to read the diff before
approving it.

### Secrets it reads

The job declares `environment: release`, so all four secrets live on that **environment**, not on
the repository. The environment has two protection rules:

- **Required reviewer:** a run that reaches the signing job waits for approval from the repository
owner, so a tag pushed by a compromised token or a modified workflow cannot sign on its own.
- **Deployment branches and tags:** only `v*` tags may deploy into it, so a branch push or a pull
request can never load these secrets, even if its workflow asks for the environment.

| Secret | Value |
| --- | --- |
| `SIDEKIT_KEYSTORE_BASE64` | the `.jks` file, base64-encoded |
| `SIDEKIT_KEYSTORE_PASSWORD` | store password |
| `SIDEKIT_KEY_ALIAS` | `manuel-sidekit` |
| `SIDEKIT_KEY_PASSWORD` | key password |

```bash
# create the environment once (owner approval + v* tags only)
gh api --method PUT repos/<owner>/Sidekit/environments/release --input - <<'JSON'
{"reviewers": [{"type": "User", "id": <user-id>}],
 "deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}
JSON
gh api --method POST repos/<owner>/Sidekit/environments/release/deployment-branch-policies \
  -f name='v*' -f type=tag

base64 -w0 ~/.android-keys/manuel-sidekit-release.jks \
  | gh secret set SIDEKIT_KEYSTORE_BASE64 --env release
gh secret set SIDEKIT_KEYSTORE_PASSWORD --env release   # paste when prompted, never on the command line
gh secret set SIDEKIT_KEY_ALIAS --env release
gh secret set SIDEKIT_KEY_PASSWORD --env release
```

Keep them environment-scoped. A repository secret is readable by every workflow in the repository,
including one added on a branch, which defeats the point of the approval gate.

If `SIDEKIT_KEYSTORE_BASE64` is missing the job fails with an explanation instead of uploading an
unsigned or debug-signed APK. The keystore file itself never enters the repository: `gitleaks` scans
for it, and `~/.android-keys/` is outside the working tree.

### Re-cutting a release

Dispatch the workflow manually from an existing tag. `gh release upload --clobber` replaces the
assets for that version in place, which is the intended way to fix a bad signature or a missing
upload without moving the tag. `versionCode` and `versionName` do not change, so devices upgrade
normally; only the signature block differs.

The expected certificate SHA-256 is pinned in `release-apk.yml`
(`c7834fd5a5204d2f334a77aab56506b317afa4001b96d8e71b0321c5a0f8cd03`). Rotating keys means changing
that constant — and knowing that no existing install will accept the new key.
