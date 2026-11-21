package hrln.interactive.sidekit.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * Sidekit's own language, chosen in Settings → Language independently of the phone's language.
 *
 * Android 13+ (API 33) keeps a per-app language itself: [select] hands it to [LocaleManager], and
 * the choice also shows under the phone's Settings → Apps → Sidekit → Language. MainActivity
 * handles `locale` configuration changes itself, so Compose redraws in the new language in place;
 * read strings with `stringResource` or `LocalResources`, not `LocalContext`, so they follow it. Android 12 has no per-app language, so the choice is
 * stored in Sidekit's preferences and [wrap] applies it to the activities' and the application's
 * contexts. There, text resolved through the application context (notifications, services)
 * switches language the next time Sidekit's process starts.
 *
 * Strings live in the `strings_<area>.xml` files under `res/values` and its translated
 * `res/values-<lang>` folders; docs/localisation.md describes adding a string or a language.
 */
object AppLanguage {
    /**
     * The languages Sidekit ships, as BCP 47 tags, in the order Settings lists them. Keep in step
     * with the `res/values-<lang>` folders; `generateLocaleConfig` builds the system's list from
     * those folders.
     */
    val supported: List<String> = listOf("en", "de", "es", "fr")

    private const val PREFS = "sidekit_settings"
    private const val KEY = "app_language"

    /** The chosen language tag (one of [supported]), or null to follow the phone's language. */
    fun selected(context: Context): String? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }
        return tag?.let(::match)
    }

    /** Switches Sidekit to [tag] (null: the phone's language) and redraws [activity] in it. */
    fun select(activity: Activity, tag: String?) {
        if (tag == selected(activity)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Delivered to the running activity as a configuration change.
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        } else {
            // commit, not apply: the recreated activity reads it back in attachBaseContext.
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putString(KEY, tag) }
            activity.recreate()
        }
    }

    /**
     * Android 12 only: [base] with the chosen language applied, for `attachBaseContext`. On
     * Android 13+ the system applies the per-app language, so [base] is returned unchanged.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        // Dates and numbers formatted with the default locale follow the chosen language too.
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    /** The language's own name for the picker, e.g. "Deutsch" for "de". */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** The activity behind a Compose `LocalContext`, which may be wrapped (theme, locale). */
    tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /** The supported tag with the same language as [tag] ("en-AU" → "en"), if there is one. */
    internal fun match(tag: String): String? {
        val language = Locale.forLanguageTag(tag).language
        return supported.firstOrNull { Locale.forLanguageTag(it).language == language }
    }
}
