package hrln.interactive.sidekit.gradle

import org.gradle.api.Project

/**
 * Version metadata read from git, so every build reports the code it was made from: a tagged
 * commit is exactly "1.2.3", anything after it is "1.2.3-3-g<sha>", and uncommitted changes add
 * "-dirty". [code] counts commits, so it only ever grows.
 *
 * A checkout without git (exported source, a CI tarball) falls back to the last release.
 */
object GitVersion {
    private const val FALLBACK_NAME = "1.2.3"
    private const val FALLBACK_CODE = 6

    fun name(project: Project): String = runCatching {
        val described = git(project, "describe", "--tags", "--match", "v[0-9]*", "--dirty", "--always")
        val name = described.removePrefix("v")
        when {
            name.isEmpty() -> FALLBACK_NAME
            name.first().isDigit() -> name
            // `--always` prints the bare sha when no release tag is reachable (a shallow clone,
            // say), so such a build reads "0.0.0-g<sha>" rather than passing itself off as the
            // last release.
            else -> "0.0.0-g$name"
        }
    }.getOrDefault(FALLBACK_NAME)

    fun code(project: Project): Int = runCatching {
        git(project, "rev-list", "--count", "HEAD").toIntOrNull() ?: FALLBACK_CODE
    }.getOrDefault(FALLBACK_CODE)

    private fun git(project: Project, vararg args: String): String = project.providers.exec {
        commandLine("git", *args)
        workingDir = project.rootDir
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}
