pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "RedSquare"
include(":core", ":app", ":push-gateway")

include(":rich-editor-compat")
project(":rich-editor-compat").projectDir = file("third-party/compose-rich-editor")
