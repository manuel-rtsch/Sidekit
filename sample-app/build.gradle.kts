plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("sidekit.version")
}

val sidekitVersionName: String by extra
val usePublishedSidekit = providers.gradleProperty("sidekitUsePublished")
    .map(String::toBoolean)
    .getOrElse(false)

android {
    namespace = "hrln.interactive.sidekit.sample"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "hrln.interactive.sidekit.sample"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = sidekitVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Normal development uses the project dependency. CI also builds this module with
    // -PsidekitUsePublished=true after publishing to build/local-repo, which exercises the POM,
    // transitive sidekit-design dependency and consumer rules.
    if (usePublishedSidekit) {
        implementation("hrln.interactive.sidekit:sidekit-sdk:$sidekitVersionName")
    } else {
        implementation(project(":sidekit-sdk"))
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
