# Third-party apps branch audit

**Branch:** `feat/third-party-apps` (based on `upstream/main` at `3debe06`)
**Audited:** 11 November 2026
**Status:** Foundation is sound; the must-fix items are done. Rebase onto `upstream/main` and run
the two instrumentation tests on a device before opening the PR.

## Branch strategy

- `upstream/main` is the canonical base for upstreamable work. `origin/main` is identical.
- `feat/third-party-apps` is based on `upstream/main` and stays there. Do not base it on
  `integration` or `release/1.3.0`.
- `release/1.3.0` already contains the original third-party-apps merge. It is the shipping
  target for fork builds, not a development base.
- `integration` is a long-lived merge branch, 183 commits ahead of main and not a superset of
  `release/1.3.0`. Do not use it as the base for this work.

## Decision

For v1 we accept **unrestricted sharing of the carefully selected preferences** through the
exported, read-only provider and the `normal` `READ_APP_CONFIG` permission.

- No allowlist, signature check, or opt-out in v1.
- No launch-only model: apps keep standalone launch and the provider stays the source of truth.
- The contract remains narrow: theme, compact layout, input mode, keypad module, custom module,
  keypad profiles, setup state, schema, host version, and capabilities.
- Hidden apps, home order, dock, mail/bank/SMS data, credentials, and T9 user words stay private.

The only transparency item we still want is the Settings/About disclosure, so users can see that
SDK-built apps can read the theme and keypad setup.

## Must fix before PR

### Merge and review

- [x] **Keep the branch based on `upstream/main`.** The branch is 5 commits ahead of
      `3debe06` and 0 behind. Rebase onto `upstream/main` again before opening the PR.
- [x] **Drop formatter-only churn.** Formatter-only files were reverted to `upstream/main` and
      only semantic SDK-extraction edits were re-applied. The remaining diffs in files such as
      `VideoScreens.kt`, `OnboardingScreen.kt`, `SettingsViewModel.kt`, `BankingRepository.kt`,
      `MusicScreens.kt`, `PhotosScreens.kt`, and `ArrangeHomeScreen.kt` are the actual SDK moves.

### Correctness and completeness

- [x] **Fix or remove `launchRoute`.** `NativeApp.launchIntent()` now sends
      `SidekitContract.EXTRA_LAUNCH_ROUTE` when the descriptor declares a route, and the launch
      tests cover both the intent and the started activity.
- [x] **Validate publishing locally.** The publish convention now stages artifacts in
      `build/local-repo`, and the `Published SDK consumer` CI job builds `:sample-app` with
      `-PsidekitUsePublished=true` against `hrln.interactive.sidekit:sidekit-sdk:<version>`.
- [x] **Publish to GitHub Packages.** `-PsidekitPublishGitHub=true` adds the
      `sidekitGitHub` repository, and the `Publish SDK` workflow publishes both libraries on
      `v*` tags with `packages: write`. Making the published coordinates the default for
      `:sample-app` waits for the first release.
- [x] **Add the Settings/About disclosure.** About now says that SDK-built apps can read the case
      colour and keypad setup, and names what stays private.
- [x] **Add a separate-APK acceptance test.** `:sample-app` is a separate APK and now has an
      instrumentation test that calls `SidekitConfigClient.status/read` from its own package.
- [x] **Verify `MIN_SCHEMA` metadata type.** `NativeApps` accepts both the manifest's string value
      and an Int bundle value, and a test covers the string form.

### Performance and API ergonomics

- [x] **Cache host configuration.** `CachingSidekitConfigSource` keeps one snapshot until the
      host notifies; `HostKeypadPreferences` and `HostSidekitThemeStore` use it.
- [x] **Filter provider notifications.** The provider only notifies for contract keys; hidden
      apps, setup step, list layout and the legacy T9 flag no longer wake clients.
- [x] **Move `NativeApps.discover()` off the main thread.** `rememberNativeApps` queries on
      `Dispatchers.IO`, caches the result, and refreshes on launcher resume.
- [x] **Add a host-or-local store factory.** `SidekitStores.forHostOrLocal(context, intent)` is
      used by `SidekitActivity`, so a consumer gets host-backed stores by default.
- [x] **Make `:sample-app` a real consumer.** It now extends `SidekitActivity`, calls
      `SidekitConfigClient.status/read`, reads the host theme, and falls back to local settings.
- [x] **Validate `EXTRA_HOST_AUTHORITY`.** The client only accepts known Sidekit authorities and
      rejects a provider whose package does not own the authority.
- [x] **Decide on explicit-authority fallback.** A missing or unsupported dev host falls back to
      the other known Sidekit builds; an unknown authority is rejected.
- [x] **Enforce capabilities.** `SidekitConfig.hasCapability()` gates theme and keypad reads.
- [x] **Add a real permission-denial test.** `:permission-test-app` is a separate APK that
      deliberately does not depend on `:sidekit-sdk` (whose manifest declares the read
      permission). Its instrumentation test resolves the provider and asserts that `call` throws
      `SecurityException` for both the stable and dev authorities.

### API surface cleanup

- [x] **Review the `:sidekit-sdk` public surface.** The contract, client, stores, shell, keypad
      settings UI, crypto, media, system utilities, and `UndoWindow` are intentionally available
      to SDK apps. The wire format and preference-file names are `@RestrictTo(LIBRARY_GROUP)`, and
      `CachingSidekitConfigSource` is `internal`. A separate internal artifact remains an option
      if the utility surface grows.
- [x] **Hide implementation details.** `SidekitPreferenceFiles` and `SidekitConfigCodec` are
      marked `@RestrictTo(LIBRARY_GROUP)`; a separate internal artifact is still an option.

## Known integration pitfall: host permission ownership

`sidekit-sdk` declares `READ_APP_CONFIG` in its library manifest, so every consumer inherits the
declaration. A differently signed consumer installed beside Sidekit then fails with
`INSTALL_FAILED_DUPLICATE_PERMISSION`. Consumers must strip the inherited `<permission>` with
`tools:node="remove"` and keep only the `<uses-permission>`. See
[third-party-apps-permissions.md](third-party-apps-permissions.md). The durable fix is to move the
declaration into the Sidekit host manifest in a future SDK release.

## Accepted for v1

- Unrestricted `normal`-permission sharing of the selected contract.
- No allowlist, signature check, or user opt-out.
- No launch-only model; standalone launch remains supported.
- No migration of Sideplay credentials, queue, or history; a reset is accepted.

## Verification performed

- `:sidekit-sdk:compileDebugKotlin`, `:app:compileDebugKotlin` and `:sample-app:compileDebugKotlin` pass.
- Full unit suites for `:app`, `:sidekit-sdk` and `:sidekit` pass.
- `:sample-app:compileDebugAndroidTestKotlin` and `:permission-test-app:assembleDebugAndroidTest`
  pass; both separate-APK tests are ready for a device.
- `spotlessCheck` passes.
- `:app:lintDebug`, `:sidekit:lintDebug`, `:sidekit-sdk:lintDebug`, `:sample-app:lintDebug` and
  `:permission-test-app:lintDebug` pass.
- `:sample-app:assembleDebug -PsidekitUsePublished=true` resolves the freshly published
  `sidekit-sdk` and its transitive `sidekit-design` dependency.
- `:sidekit:publishAllPublicationsToSidekitGitHubRepository -PsidekitPublishGitHub=true` is
  configured and dry-run clean; the `Publish SDK` workflow runs it on `v*` tags.
- `:sidekit` and `:sidekit-sdk` publish to `build/local-repo`, and `:sample-app:assembleDebug
  -PsidekitUsePublished=true` resolves the published `sidekit-sdk` plus its transitive
  `sidekit-design` dependency.

## Size

- 12 commits, 103 files, +4,956 / −968 (net +3,988).
- Net production code is roughly +2.1k lines, mostly the new `:sidekit-sdk` module.
- Tests: 16 files, +700 / −60. Docs: 2 files, +520 / −4. Build/publish: 16 files, +360 / −55.
