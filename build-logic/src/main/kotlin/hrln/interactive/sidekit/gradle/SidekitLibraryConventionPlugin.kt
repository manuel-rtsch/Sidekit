package hrln.interactive.sidekit.gradle

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Shared Android library setup for every publishable Sidekit module. */
class SidekitLibraryConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.android.library")
        project.pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        project.extensions.configure<LibraryExtension> {
            compileSdk {
                version = release(37)
            }

            defaultConfig {
                minSdk = 31
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }

            buildFeatures {
                compose = true
            }

            // Published AARs share the Sidekit resource namespace, so consumers cannot collide
            // with their own resources.
            resourcePrefix = "sk_"

            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                }
            }
        }
    }
}
