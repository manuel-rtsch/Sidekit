# Localisation

Sidekit ships in English, German, Spanish and French. Every string a person sees on screen, hears
from TalkBack or reads in a notification comes from Android string resources, so a new language is
a folder of translations and no code.

## Choosing a language

**Settings → Language** lists *Phone language* and each shipped language, named in itself
(*Deutsch*, *Español*, …). [`AppLanguage`](../app/src/main/java/hrln/interactive/sidekit/locale/AppLanguage.kt)
applies the choice:

- **Android 13 and later** use the system's per-app language (`LocaleManager`). The choice also
  appears under the phone's *Settings → Apps → Sidekit → Language*. `MainActivity` handles
  `locale` configuration changes itself, so the screen redraws in the new language without a
  restart.
- **Android 12** has no per-app language. The choice is saved in Sidekit's preferences, the
  activity is recreated, and `attachBaseContext` in `MainActivity`, `RingingActivity` and
  `SidekitApplication` applies it. Text resolved through the application context (notifications,
  services) switches language the next time Sidekit's process starts.

`generateLocaleConfig` in `app/build.gradle.kts` builds the system's list of languages from the
`res/values-<lang>` folders. `res/resources.properties` says the untranslated `values/` folder is
English.

Dates, times and numbers follow the chosen language through `Locale.getDefault()`. The T9 word
list is English only, so predictive typing still suggests English words in every language.

## Where strings live

Each area of the app has its own file, so two changes to different apps never touch the same file:

| Module | English | Translations |
| --- | --- | --- |
| `app` | `app/src/main/res/values/strings_<area>.xml` | `app/src/main/res/values-<lang>/strings_<area>.xml` |
| `sidekit` (design library) | `sidekit/src/main/res/values/strings_sidekit.xml` | `sidekit/src/main/res/values-<lang>/…` |
| `sidekit-sdk` | `sidekit-sdk/src/main/res/values/strings_sdk.xml` | `sidekit-sdk/src/main/res/values-<lang>/…` |

Names start with the area (`mail_`, `weather_`) because every module's strings merge into one
`R.string` namespace. The library modules also require the `sk_` resource prefix, so their names
start `sk_sidekit_` and `sk_sdk_`. A translation file has the same name and the same keys as the
English one.

## Writing a string

- In a composable, use `stringResource(R.string.mail_inbox)`, `stringResource(R.string.x, arg)` and
  `pluralStringResource(R.plurals.x, count, count)`. For text resolved later in a click handler or
  coroutine, read `LocalResources.current` rather than `LocalContext.current.getString`, and the
  locale from `LocalConfiguration.current.locales[0]`: both follow a language change, which
  lint's `LocalContextGetResourceValueCall` and `NonObservableLocale` checks enforce.
- Outside composition (view models, services, notifications, workers), use
  `context.getString(…)` or `resources.getQuantityString(…)`. A model that is built without a
  `Context` holds an `@StringRes Int` and the UI resolves it.
- Build sentences with positional placeholders (`%1$s`, `%2$d`), never by concatenating fragments:
  word order changes between languages.
- Use `<plurals>` for anything with a count. English has `one` and `other`; French also treats 0 as
  `one`.
- Escape `'` as `\'` and `"` as `\"`. Keep `…`, `—` and `→` as the characters themselves.
- Mark brand and product names (*Sidekit*, *Sidemessage*, *Sideport*, *Up*) and strings that are
  data rather than prose (formats, codes, units) `translatable="false"`, in English only.
- Comment a string when its context is not obvious from its name: a short label on a 480 px wide
  screen needs a short translation.

Not localised: log messages, exception text that never reaches the screen, preference keys, routes,
analytics, and anything sent to or parsed from a server.

## Adding a language

1. Copy each `values/strings_*.xml` in `app`, `sidekit` and `sidekit-sdk` to `values-<lang>/`,
   drop the `translatable="false"` entries and translate the rest.
2. Add the tag to `AppLanguage.supported`.
3. Run `python3 scripts/check_translations.py`, then `./gradlew lintDebug`.

## Checks

`python3 scripts/check_translations.py` fails if a translation is missing a key, has one English no
longer has, changes a string's placeholders or leaves a quote unescaped. Lint's
`MissingTranslation` and `StringFormatMatches` checks enforce the same rules in CI.
