# Librespot patches used by Sidetracks

Sidetracks pins librespot 0.8.0 in `native/sidekit-spotify/Cargo.lock`. One minimal source patch
is currently applied through a vendored copy at `native/vendor/librespot-core`, wired in with
`[patch.crates-io]` in `native/sidekit-spotify/Cargo.toml`.

## 1. Treat Android as desktop for Login5/client-token

| | |
|---|---|
| **File** | `native/vendor/librespot-core/src/config.rs` |
| **Upstream value** | `pub const OS: &str = std::env::consts::OS;` |
| **Sidetracks value** | `pub const OS: &str = "linux";` |
| **Why** | The OAuth token comes from Spotify's desktop/keymaster client id (`65b708073fc0480ea92a077233ca87bd`). Login5 and client-token requests bind to the client id implied by `OS`; on Android, librespot otherwise uses its Android client id and rejects the stored credential with `INVALID_CREDENTIALS`/`BAD_REQUEST`. |
| **Scope** | One constant. No protocol, playback, audio, or authentication logic is changed. |
| **Removal** | Delete the patch and the `[patch.crates-io]` block once librespot exposes a supported way to override the platform/client identity, or once Sidetracks performs its own Android-client OAuth flow. |
| **Licence** | librespot is MIT. The vendored copy keeps its upstream `LICENSE`. |

The vendored tree is otherwise byte-for-byte the crates.io 0.8.0 release. The only other difference
is that `LICENSE` is retained inside the vendored directory.

## Verification

```bash
diff -rq ~/.cargo/registry/src/index.crates.io-*/librespot-core-0.8.0 \
  native/vendor/librespot-core
```

Expected: only `src/config.rs` differs (plus registry bookkeeping files that are not shipped).
