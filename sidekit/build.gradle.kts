plugins {
    id("sidekit.android.library")
    id("sidekit.publish")
}

android {
    namespace = "hrln.interactive.sidekit.design"
}

sidekitPublish {
    artifactId.set("sidekit-design")
    description.set("Sidekit's Compose design system, keypad and T9 input")
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui.tooling.preview)
    // Part of the public API: SidekitCanvas uses BackHandler, Dictation observes the lifecycle,
    // and Keypad takes a CoroutineScope.
    api(libs.androidx.activity.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.common)
    api(libs.kotlinx.coroutines.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
