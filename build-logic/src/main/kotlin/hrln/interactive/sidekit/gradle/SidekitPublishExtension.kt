package hrln.interactive.sidekit.gradle

import org.gradle.api.provider.Property

/** Maven coordinates and metadata for a publishable Sidekit library. */
abstract class SidekitPublishExtension {
    abstract val artifactId: Property<String>
    abstract val description: Property<String>
}
