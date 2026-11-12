plugins {
    id("sidekit.android.library")
    id("sidekit.publish")
}

android {
    namespace = "hrln.interactive.sidekit.sdk"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

sidekitPublish {
    artifactId.set("sidekit-sdk")
    description.set("Contract, host integration and shared shell for Sidekit-native apps")
}

dependencies {
    api(project(":sidekit"))
    api(libs.androidx.core.ktx)
    api(libs.androidx.activity.compose)
    api(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
}
