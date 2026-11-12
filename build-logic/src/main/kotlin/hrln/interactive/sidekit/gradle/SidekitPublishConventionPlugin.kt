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
 * coordinates without credentials. GitHub Packages is added when `-PsidekitPublishGitHub=true`
 * is set, using the workflow's `GITHUB_REPOSITORY`, `GITHUB_ACTOR` and `GITHUB_TOKEN`.
 */
class SidekitPublishConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("maven-publish")
        project.group = "hrln.interactive.sidekit"
        project.version = GitVersion.name(project)

        val publishGitHub = project.providers.gradleProperty("sidekitPublishGitHub")
            .map(String::toBoolean)
            .getOrElse(false)
        val githubRepository = project.providers.environmentVariable("GITHUB_REPOSITORY").orNull
        val githubToken = project.providers.environmentVariable("GITHUB_TOKEN").orNull
        val githubActor = project.providers.environmentVariable("GITHUB_ACTOR").orNull

        project.extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "sidekitLocal"
                    url = project.rootProject.layout.buildDirectory.dir("local-repo").get().asFile.toURI()
                }
                if (publishGitHub) {
                    require(!githubRepository.isNullOrBlank()) {
                        "GITHUB_REPOSITORY is required when publishing to GitHub Packages."
                    }
                    require(!githubToken.isNullOrBlank()) {
                        "GITHUB_TOKEN is required when publishing to GitHub Packages."
                    }
                    maven {
                        name = "sidekitGitHub"
                        url = project.uri("https://maven.pkg.github.com/$githubRepository")
                        credentials {
                            username = githubActor ?: "github"
                            password = githubToken
                        }
                    }
                }
            }
        }

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
                            url.set("https://github.com/manuel-rtsch/Sidekit")
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
