package hrln.interactive.sidekit.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Exposes git-derived version metadata to build scripts working with the Android DSL. */
class SidekitVersionConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.extraProperties["sidekitVersionName"] = GitVersion.name(project)
        project.extensions.extraProperties["sidekitVersionCode"] = GitVersion.code(project)
    }
}
