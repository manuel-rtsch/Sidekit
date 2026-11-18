# Sidekit

Sidekit is a home-screen launcher and suite of apps for the Sidephone, the
keypad Android phone. One APK puts a launcher and two dozen keypad-first apps on the phone: messages,
notes, contacts, mail, maps, music, a browser, banking and more. Every app is designed to work on the
T9 or Mini QWERTY keypad, the D-pad and the touchscreen.

![Sidekit launcher](app/screenshots/launcher_grid_53-1129.png)

## Install

You need a phone running Android 12 (API 31) or later.

1. Download the `.apk` from the [latest release](https://github.com/HarlenPostill/Sidekit/releases/latest).
2. Open it on the phone and allow installs from that source, or install it from a computer with
   `adb install -r <file>.apk`.
3. Go home and choose **Sidekit** as the Home app.

First launch opens a short setup that covers the Home app, case colour, layout, permissions and the
keypad. [docs/onboarding.md](docs/onboarding.md) describes each step.

## What's inside

| | |
| --- | --- |
| Communication | Sidemessage (SMS/MMS), Phonebook, Mail (IMAP/SMTP) |
| Productivity | Sidenotes, Sideticks, Calendar, Calculator, Clock, Files, Vault |
| Out and about | Maps, Trip Planning, Sideweather, Compass, Translate, Web browser |
| Media | Photos, Camera, Music, Video |
| More | Banking (Up, read-only), Health, Settings, Apps, Sideport |

Sideport opens other Android apps inside Sidekit's design. Accounts are optional: every app works
without one, and Mail and Banking only connect when you add your own details.

## Build from source

Requirements: JDK 21 and the Android SDK (compile SDK 37). Android Studio sets up both.

```bash
git clone https://github.com/HarlenPostill/Sidekit.git
cd Sidekit
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. It installs as `hrln.interactive.sidekit.dev`
next to a release build.

### Project layout

| Path | What it holds |
| --- | --- |
| `app/` | The launcher and every built-in app (`app/src/main/java/hrln/interactive/sidekit/apps/`) |
| `sidekit/` | The shared design system: theme, components, action bar, keypad input and the T9 dictionary |
| `app/screenshots/` | Roborazzi screenshots of each screen at the Sidephone canvas size |
| `docs/` | Setup, keypad mapping, service integrations and CI notes |
| `site/` | The setup guide web page |
| `tools/`, `scripts/` | The T9 word-list builder and a transit API probe |

### Checks

These are the same checks CI runs on every pull request ([docs/ci.md](docs/ci.md)):

```bash
./gradlew spotlessApply spotlessCheck   # ktlint, on files changed since main
./gradlew testDebugUnitTest             # JVM, Robolectric and screenshot tests
./gradlew lintDebug
./gradlew :app:assembleDebug :app:assembleRelease
```

The unit tests re-record the screenshots in `app/screenshots/`. Commit only the ones for screens you
changed.

## Contributing

Issues and pull requests are welcome. Branch from `main`, keep each PR focused, and fill in the
[pull request template](.github/pull_request_template.md). App changes need testing on a physical
device before they merge.

## Docs

- [Setup and launcher search](docs/onboarding.md)
- [Keypad mapping](docs/keypad-mapping.md)
- [Service integrations](docs/integrations.md)
- [Continuous integration](docs/ci.md)

## License

[MIT](LICENSE). The T9 word list is derived from FrequencyWords (CC BY-SA 4.0); see
[sidekit/src/main/assets/t9/NOTICE](sidekit/src/main/assets/t9/NOTICE).
