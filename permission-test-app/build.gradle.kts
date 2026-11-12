plugins {
    alias(libs.plugins.android.application)
    id("sidekit.version")
}

android {
    namespace = "hrln.interactive.sidekit.permissiontest"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "hrln.interactive.sidekit.permissiontest"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Deliberately no :sidekit-sdk dependency: the SDK manifest declares the read permission, and
    // this APK must stay without it so the provider sees a real unprivileged caller.
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
