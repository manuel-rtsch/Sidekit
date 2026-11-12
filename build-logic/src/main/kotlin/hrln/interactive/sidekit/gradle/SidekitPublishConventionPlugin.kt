package hrln.interactive.sidekit.gradle

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create

/**
 * Maven publication for a Sidekit Android library.
 *
 * A local staging repository is configured so CI can build a consumer against the published
 * coordinates without credentials. The repository that is actually served to third-party apps is
 * added when `-PsidekitPublishPages=true` is set: it stages into `build/pages-repo`, which the
 * publish workflow pushes to the `gh-pages` branch as a static Maven repository. Consumers need no
 * credentials, unlike GitHub Packages.
 */
class SidekitPublishConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("maven-publish")
        project.group = "hrln.interactive.sidekit"
        project.version = GitVersion.name(project)

        val publishPages = project.providers.gradleProperty("sidekitPublishPages")
            .map(String::toBoolean)
            .getOrElse(false)
        val githubRepository = project.providers.environmentVariable("GITHUB_REPOSITORY").orNull

        project.extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "sidekitLocal"
                    url = project.rootProject.layout.buildDirectory.dir("local-repo").get().asFile.toURI()
                }
                if (publishPages) {
                    // Staged, not uploaded: the publish workflow copies this tree into the
                    // gh-pages branch, where GitHub Pages serves it without authentication.
                    maven {
                        name = "sidekitPages"
                        url = project.rootProject.layout.buildDirectory.dir("pages-repo").get().asFile.toURI()
                    }
                }
            }
        }

        // The POM points at whichever repository runs the workflow, so a fork's artifacts still
        // describe where the code lives, with the canonical project as the fallback.
        val projectUrl = githubRepository?.let { "https://github.com/$it" }
            ?: "https://github.com/HarlenPostill/Sidekit"

        val sidekitPublish = project.extensions.create<SidekitPublishExtension>("sidekitPublish")
        sidekitPublish.artifactId.convention(project.name)
        sidekitPublish.description.convention("Sidekit library ${project.name}")

        project.plugins.withId("com.android.library") {
            project.extensions.configure<LibraryExtension> {
                publishing {
                    singleVariant("release") {
                        withSourcesJar()
                    }
                }
            }
        }

        project.afterEvaluate {
            val release = project.components.findByName("release") ?: error(
                "No release component found; apply sidekit.android.library before sidekit.publish.",
            )
            project.extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") {
                        from(release)
                        artifactId = sidekitPublish.artifactId.get()
                        pom {
                            name.set(project.name)
                            description.set(sidekitPublish.description.get())
                            url.set(projectUrl)
                            licenses {
                                license {
                                    name.set("MIT License")
                                    url.set("https://opensource.org/licenses/MIT")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
