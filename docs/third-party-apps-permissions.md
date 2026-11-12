# Third-party app permissions and the host config provider

The SDK exposes Sidekit's theme and keypad configuration through an exported, read-only provider.
Access is guarded by `hrln.interactive.sidekit.permission.READ_APP_CONFIG`.

## The duplicate-permission problem

`sidekit-sdk` currently declares the permission in its own library manifest:

```xml
<permission
    android:name="hrln.interactive.sidekit.permission.READ_APP_CONFIG"
    android:protectionLevel="normal" />
<uses-permission android:name="hrln.interactive.sidekit.permission.READ_APP_CONFIG" />
```

Manifest merging copies both elements into every app that depends on the SDK. Android allows a
`normal` permission to be declared by only one installed package. If a second app declares the
same permission with a **different signing certificate**, installation fails with:

```
INSTALL_FAILED_DUPLICATE_PERMISSION: Package <client> attempting to redeclare permission
hrln.interactive.sidekit.permission.READ_APP_CONFIG already owned by hrln.interactive.sidekit
```

This was observed with a release-signed Sideplay installed beside Sidekit. Debug builds happened
to share the debug certificate, so the conflict stayed hidden during development.

## What a consumer app must do now

An app that embeds `sidekit-sdk` and may coexist with Sidekit should remove the inherited
declaration while keeping the request:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <permission
        android:name="hrln.interactive.sidekit.permission.READ_APP_CONFIG"
        tools:node="remove" />
    <uses-permission android:name="hrln.interactive.sidekit.permission.READ_APP_CONFIG" />
    ...
</manifest>
```

The `uses-permission` remains harmless if Sidekit is not installed. When Sidekit is present it
owns the definition and the client can read the provider.

Sideplay applies this in `app/src/main/AndroidManifest.xml` (commit `f05a35b`).

## Intended SDK fix

Move ownership to the host:

1. Remove the `<permission>` element from `sidekit-sdk/src/main/AndroidManifest.xml`; keep the
   `<uses-permission>` there so consumers still request access.
2. Declare the permission in the Sidekit app manifest
   (`hrln.interactive.sidekit`), where the provider lives.
3. Keep the protection level `normal` unless the contract changes to a signature check.
4. Ship the change in a new SDK version (for example `1.4.2`). Consumers built against `1.4.1`
   still need the `tools:node="remove"` workaround.

If a client is installed before Sidekit, it has no grant for the permission until it is
reinstalled after the host defines it. Document that edge case, or have the client check the
provider's availability and prompt for a reinstall.
