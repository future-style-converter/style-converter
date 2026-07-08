pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "StyleConverterTest"
include(":app")

// R3: the runtime style engine lives outside this build's directory, at
// repo-root/runtimes/compose. Gradle fully supports out-of-tree module
// dirs — this keeps a single Android Gradle build (rooted here) while the
// library sits beside the other runtime engines (runtimes/web, …).
include(":runtime")
project(":runtime").projectDir = File(rootDir, "../../runtimes/compose")
