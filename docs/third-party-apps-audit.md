# Third-party apps branch audit

**Branch:** `feat/third-party-apps` (up to date with `upstream/main` at `4886a94`)
**Audited:** 11 November 2026, re-audited after the `upstream/main` merge
**Status:** Foundation is sound; the must-fix items are done. Run the two instrumentation tests on
a device before opening the PR.

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

- [x] **Keep the branch based on `upstream/main`.** The branch is 0 commits behind
      `upstream/main`; upstream's keypad, Sundial and Arrange apps work is merged in, with the
      SDK's stores and input controller adapted to it.
- [x] **Drop formatter-only churn.** Formatter-only files were reverted to `upstream/main` and
      only semantic SDK-extraction edits were re-applied. What still looks like formatting in
      `VideoScreens.kt`, `OnboardingScreen.kt`, `PhotosScreens.kt`, `MusicScreens.kt` and others is
      ktlint's `android_studio` style, which the shared Spotless config applies to every file the
      branch touches: it drops trailing commas and expands single-line `if`/`else` bodies across the
      whole file. Those files also carry a semantic edit (the SDK imports and call-site changes),
      so `spotlessCheck` fails without the reformat.

### Correctness and completeness

- [x] **Define the read permission in the host, not the SDK.** `sidekit-sdk` declared
      `READ_APP_CONFIG`, so every consumer inherited the declaration and any consumer signed with
      a different key failed to install beside Sidekit (`INSTALL_FAILED_DUPLICATE_PERMISSION`).
      The `<permission>` element now lives in the Sidekit app manifest next to the provider; the
      SDK manifest keeps only the `<uses-permission>` request. See
      [third-party-apps-permissions.md](third-party-apps-permissions.md).
- [x] **Keep the SDK's key loop lint-clean.** `SidekitActivity` reaches
      `super.dispatchKeyEvent` through one annotated helper instead of an inline lambda, so
      `:sidekit-sdk:lintDebug` passes without a suppressed error inside the lazy field.
- [x] **Fix or remove `launchRoute`.** `NativeApp.launchIntent()` now sends
      `SidekitContract.EXTRA_LAUNCH_ROUTE` when the descriptor declares a route, and the launch
      tests cover both the intent and the started activity.
- [x] **Validate publishing locally.** The publish convention stages the published layout in
      `build/pages-repo`, and the `Published SDK consumer` CI job builds `:sample-app` with
      `-PsidekitUsePublished=true` against `hrln.interactive.sidekit:sidekit-sdk:<version>`.
- [x] **Publish to GitHub Pages.** `-PsidekitPublishPages=true` stages into `build/pages-repo`,
      and the `Publish SDK` workflow pushes that tree to `gh-pages`, which Pages serves as a
      static Maven repository (`contents: write`, `.nojekyll`, older versions kept). No package
      registry and no consumer credentials. Making the published coordinates the default for
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

### Host permission ownership

`sidekit-sdk` used to declare `READ_APP_CONFIG` in its library manifest, so every consumer
inherited the declaration and a differently signed consumer installed beside Sidekit failed with
`INSTALL_FAILED_DUPLICATE_PERMISSION`. Ownership now sits in the Sidekit app manifest, next to the
provider that uses it; the SDK manifest keeps only the `<uses-permission>` request. Consumers need
no `tools:node="remove"` workaround. See
[third-party-apps-permissions.md](third-party-apps-permissions.md).

## Accepted for v1

- Unrestricted `normal`-permission sharing of the selected contract.
- No allowlist, signature check, or user opt-out.
- No launch-only model; standalone launch remains supported.
- No migration of Sideplay credentials, queue, or history; a reset is accepted.

## Verification performed

- `:sidekit-sdk:compileDebugKotlin`, `:app:compileDebugKotlin` and `:sample-app:compileDebugKotlin` pass.
- Full unit suites for `:app`, `:sidekit-sdk` and `:sidekit` pass.
- Both separate-APK instrumentation tests pass on an API 37 emulator with the debug Sidekit
  installed: `:sample-app:connectedDebugAndroidTest` reads the host configuration from another
  package, and `:permission-test-app:connectedDebugAndroidTest` gets the expected
  `SecurityException` from the provider. The host is the only package that defines
  `READ_APP_CONFIG` (`dumpsys package permissions`), and the sample is granted it at install.
- The launcher and onboarding run on the SDK shell in that build: the case colour picked in
  onboarding recolours the home screen through the SDK theme store, and one D-pad press moves
  focus one row.
- `spotlessCheck` passes.
- `:app:lintDebug`, `:sidekit:lintDebug`, `:sidekit-sdk:lintDebug`, `:sample-app:lintDebug` and
  `:permission-test-app:lintDebug` pass.
- `:sample-app:assembleDebug -PsidekitUsePublished=true` resolves the freshly published
  `sidekit-sdk` and its transitive `sidekit-design` dependency.
- `:sidekit:publishAllPublicationsToSidekitPagesRepository -PsidekitPublishPages=true` stages the
  exact tree the workflow deploys, and `:sample-app:assembleDebug -PsidekitUsePublished=true`
  resolves both artifacts from `build/pages-repo` after clearing the Gradle cache.
- The published POM points at the repository that owns the code, and `sidekit-sdk`'s dependency
  resolves to the published `sidekit-design` artifact.
- `:sidekit` and `:sidekit-sdk` also publish to `build/local-repo` for quick local checks, and
  `:sample-app:assembleDebug -PsidekitUsePublished=true` resolves the published `sidekit-sdk`
  plus its transitive `sidekit-design` dependency.

## Size

- 30 commits (including the `upstream/main` merge), 153 files, +5,962 / −1,053 (net +4,909).
- Net production code is roughly +2.3k lines, mostly the new `:sidekit-sdk` module.
- Tests: 16 files, +700 / −60. Docs: 4 files, +740 / −9. Build/publish: 16 files, +360 / −55.

## Permission scoping (post-merge)

Moving the permission into the host manifest fixed the client-side duplicate, but both Sidekit
builds then declared the same fixed name. A release-signed stable build and a debug-signed dev
build could not be installed together:

```
INSTALL_FAILED_DUPLICATE_PERMISSION: Package hrln.interactive.sidekit.dev attempting to
redeclare permission hrln.interactive.sidekit.permission.READ_APP_CONFIG already owned by
hrln.interactive.sidekit
```

Reproduced with a release APK signed by a throwaway key beside the debug-signed dev APK. The
permission name now follows the application id (`${applicationId}.permission.READ_APP_CONFIG`),
the provider checks `SidekitContract.readPermissionForPackage(packageName)`, and the SDK requests
both known names. After the change the two builds install together, `dumpsys package permissions`
lists one definition per build, and the sample app on the Sidephone shows the dev permission
`granted=true` while the stable name is simply undefined.

## Access control (post-audit decision)

The read permission is `normal`, which Android grants to any app that requests it, so it gated
nothing. The exposure is presentation and input configuration — no personal content — but the
contract is additive by design, so the door would widen with every future field, silently.

Decision: keep the open `normal` permission as the Android-level declaration and decide access by
signing certificate (`TrustedCallers`). An app is trusted when it is signed with Sidekit's own
certificate or its certificate digest is listed in `res/values/trusted_apps.xml`; the trust list is
bundled, so adding a partner is a Sidekit release and there is no runtime approval flow.

The same policy closes the tile-injection hole: only trusted apps appear as native launcher tiles,
a `builtin:` app id is refused, and duplicate app ids collapse to one tile. Clients see
`HostStatus.NotPermitted` when the host refuses them, and hosts advertise
`SidekitContract.CAPABILITY_TRUST`.

Verified on the Sidephone with the dev host: debug-signed client reads the host; the same client
re-signed with a throwaway key reports `NotPermitted`; listing that certificate makes it readable
again (colon-separated digest form).
