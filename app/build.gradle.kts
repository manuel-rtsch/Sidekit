plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("sidekit.version")
}

// Version metadata is read from git by the shared `sidekit.version` convention plugin, so a build
// always reports the code it was made from: a tagged commit is exactly "1.2.3", anything after it
// is "1.2.3-3-g<sha>", and uncommitted changes add "-dirty". `versionCode` counts commits, so it
// only ever grows and a newer build always installs over an older one. Pass
// -PsidekitVersionName=… / -PsidekitVersionCode=… to override.
val sidekitVersionName: String by extra
val sidekitVersionCode: Int by extra

android {
    ndkVersion = "27.1.12297006"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    namespace = "hrln.interactive.sidekit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "hrln.interactive.sidekit"
        minSdk = 31
        targetSdk = 36
        versionCode = providers.gradleProperty("sidekitVersionCode")
            .map(String::toInt)
            .getOrElse(sidekitVersionCode)
        versionName = providers.gradleProperty("sidekitVersionName")
            .getOrElse(sidekitVersionName)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // R8: shrinks, inlines and devirtualises. Compose is designed to be run through R8;
            // without it release builds keep debug-only code paths (e.g. composition tracing
            // checks) and run measurably slower and larger.
            optimization {
                enable = true
            }
        }
        // Release code (R8, no debug Compose checks, baseline profile) signed with the local debug
        // key, so it installs over a dev build: `./gradlew :app:installProfile`. Use this, not debug,
        // to judge animation smoothness on the Sidephone.
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        // Development build that installs alongside the stable SideKit as a separate app
        // (`hrln.interactive.sidekit.dev`), so the stable one can remain the daily driver while
        // this tracks the current branch/tip for testing. See app/src/dev/res for its distinct
        // name, and for the DEV pill and About row that mark its UI (values/build.xml). It derives from `profile` (R8 + debug signing, no debug Compose checks, baseline
        // profile) so the on-device testing build doesn't stutter like a raw debug build — it
        // also falls back to `.sidekit` release. Use `debug` for breakpoint debugging instead.
        // The version comes from git describe, so a dev build names the branch tip it was built
        // from (e.g. 1.2.3-1-g91da863-dev) rather than a stale hand-written number.
        create("dev") {
            initWith(getByName("profile"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Lists the res/values-<lang> folders in the manifest's localeConfig, so Android 13+ offers
    // Sidekit's languages under Settings → Apps → Sidekit → Language. The untranslated folder's
    // language is set in res/resources.properties.
    androidResources {
        generateLocaleConfig = true
    }
    // Settings → Language switches at runtime, so every language must be in the install: a Play
    // bundle split by language would only carry the phone's.
    bundle {
        language {
            enableSplit = false
        }
    }
    packaging { resources.merges += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt") }
    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME identifies the host build in the config contract.
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Roborazzi: write 480x640 screenshots to app/screenshots/ on every `./gradlew test` run.
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }
}

dependencies {
    implementation(project(":sidekit"))
    implementation(project(":sidekit-sdk"))
    implementation("com.sun.mail:android-mail:1.6.8")
    implementation("com.sun.mail:android-activation:1.6.8")
    implementation("com.klinkerapps:android-smsmms:5.2.6")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation("com.icegreen:greenmail:1.6.15") { exclude(group = "com.sun.mail", module = "javax.mail") }
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
