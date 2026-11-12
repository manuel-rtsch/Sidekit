# Third-party app permissions and the host config provider

The SDK exposes Sidekit's theme and keypad configuration through an exported, read-only provider.
Access is guarded by a `normal` permission that Android grants at install time:
`hrln.interactive.sidekit.permission.READ_APP_CONFIG` for the stable build and
`hrln.interactive.sidekit.dev.permission.READ_APP_CONFIG` for the dev build.

## Who declares what

The permission is **defined by the host**, in Sidekit's own manifest, next to the provider that
uses it, and its name follows the application id:

```xml
<!-- app/src/main/AndroidManifest.xml -->
<permission
    android:name="${applicationId}.permission.READ_APP_CONFIG"
    android:protectionLevel="normal" />
```

The SDK manifest only **requests** both names, so every consumer app inherits the requests and
nothing else:

```xml
<!-- sidekit-sdk/src/main/AndroidManifest.xml -->
<uses-permission android:name="hrln.interactive.sidekit.permission.READ_APP_CONFIG" />
<uses-permission android:name="hrln.interactive.sidekit.dev.permission.READ_APP_CONFIG" />
```

A request for a name nothing defines is ignored, so a client can ask for both from the start and
whichever build is installed grants its own. `SidekitContract.readPermissionForPackage` derives the
name the host checks, and `SidekitContract.READ_PERMISSIONS` lists the ones a client requests.

A consumer therefore needs no manifest changes of its own. It can ask for the permission from the
moment it is built, and reading the provider starts working as soon as Sidekit is installed.

## Why the name follows the application id

Two installed packages cannot define one `normal` permission unless they share a signing
certificate. The stable build and the dev build are separate packages but would have declared the
same name, so a release-signed stable Sidekit plus a debug-signed dev build could not be installed
together:

```
INSTALL_FAILED_DUPLICATE_PERMISSION: Package hrln.interactive.sidekit.dev attempting to
redeclare permission hrln.interactive.sidekit.permission.READ_APP_CONFIG already owned by
hrln.interactive.sidekit
```

Scoping the name to the application id removes the shared definition entirely; each build owns its
own permission and checks its own (`SidekitConfigProvider.callerCanRead`). The stable name is
unchanged, so clients built against the contract keep working.

## Who may read it

Holding the permission is necessary but not sufficient. `normal` is granted to any app that asks
for it, so it records intent, not identity — the actual decision is the caller's **signing
certificate** (`TrustedCallers`):

- an APK signed with Sidekit's own certificate is first-party and trusted;
- any other app is trusted only when the SHA-256 digest of its signing certificate is listed in
  `app/src/main/res/values/trusted_apps.xml`;
- nothing else is trusted, whatever it declares in its manifest.

```xml
<string-array name="sidekit_trusted_app_digests">
    <item>B8:29:49:FE:…</item>
</string-array>
```

Digests come from `apksigner verify -print-certs app-release.apk`; colons are optional. A debug
certificate must never be listed — the debug keystore is public, so an entry would trust anyone.

The same policy decides which apps may appear as Sidekit-native launcher tiles, so an app cannot
take a place on the home screen just by advertising `ACTION_NATIVE_APP`. A native app also cannot
claim a `builtin:` app id, and two apps claiming the same id collapse to one tile.

Adding an app is one line in the resource and a Sidekit release; there is no runtime approval flow
by design. A refused app sees `HostStatus.NotPermitted` (the host advertises
`SidekitContract.CAPABILITY_TRUST`) and keeps working on its own local settings, so a client can
tell "ask the Sidekit author to trust this build" apart from "no Sidekit here".

Verified on the Sidephone with the dev host: a debug-signed client reads the host, the same client
re-signed with another key reports `NotPermitted`, and adding that certificate to the resource
makes it readable again.

## Why the definition does not live in the SDK

Android allows a `normal` permission to be declared by exactly one installed package. When the SDK
declared it, manifest merging copied the declaration into **every** consumer, so two SDK-built
apps installed side by side — or one such app beside Sidekit — failed to install unless they
happened to share a signing certificate:

```
INSTALL_FAILED_DUPLICATE_PERMISSION: Package <client> attempting to redeclare permission
hrln.interactive.sidekit.permission.READ_APP_CONFIG already owned by hrln.interactive.sidekit
```

This was observed with a release-signed Sideplay installed beside Sidekit. Debug builds shared the
debug certificate, so the conflict stayed hidden during development.

## Order of installation

A client installed before Sidekit requests a permission that is not defined yet. Android ignores
the request until the defining package is installed; the grant then applies to the client. The
client still works in the meantime: `SidekitConfigClient.status` reports `NotInstalled` and
`SidekitStores.forHostOrLocal` falls back to the app's own writable settings. Nothing needs to be
reinstalled once Sidekit appears, and the client re-resolves the host on its next start.

## Apps built against the 1.4.1 SDK

`1.4.1` was published with the declaration still inside the SDK, so an app built against it and
signed with a different key than Sidekit must strip the inherited element:

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

That workaround becomes unnecessary — and should be removed — when the app moves to an SDK built
after this change.
