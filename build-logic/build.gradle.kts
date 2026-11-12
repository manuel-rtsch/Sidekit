plugins {
    `kotlin-dsl`
}

group = "hrln.interactive.sidekit.buildlogic"

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("sidekitAndroidLibrary") {
            id = "sidekit.android.library"
            implementationClass = "hrln.interactive.sidekit.gradle.SidekitLibraryConventionPlugin"
        }
        create("sidekitVersion") {
            id = "sidekit.version"
            implementationClass = "hrln.interactive.sidekit.gradle.SidekitVersionConventionPlugin"
        }
        create("sidekitPublish") {
            id = "sidekit.publish"
            implementationClass = "hrln.interactive.sidekit.gradle.SidekitPublishConventionPlugin"
        }
    }
}
