pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Local staging repository used by the published-consumer CI job. It is empty during
        // normal development, where :sample-app uses the project dependency instead.
        maven {
            name = "sidekitLocal"
            url = uri(rootDir.resolve("build/local-repo"))
        }
        // The exact tree the publish workflow deploys to GitHub Pages, so a release can be
        // verified locally before it is pushed. Consumers use the https:// URL instead.
        maven {
            name = "sidekitPages"
            url = uri(rootDir.resolve("build/pages-repo"))
        }
    }
}

rootProject.name = "Sidekit"
include(":app")
include(":sidekit")
include(":sidekit-sdk")
include(":sample-app")
include(":permission-test-app")
