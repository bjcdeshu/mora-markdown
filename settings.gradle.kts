pluginManagement {
    val mirrorRoot = providers.environmentVariable("MORA_MAVEN_MIRROR").orNull

    repositories {
        if (mirrorRoot != null) {
            maven {
                url = uri("$mirrorRoot/google")
                content {
                    includeGroupByRegex("com\\.android(\\..*)?")
                    includeGroupByRegex("androidx\\..*")
                    includeGroupByRegex("com\\.google\\.android(\\..*)?")
                }
            }
            maven {
                url = uri("$mirrorRoot/maven")
            }
        } else {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    val mirrorRoot = providers.environmentVariable("MORA_MAVEN_MIRROR").orNull

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (mirrorRoot != null) {
            maven {
                url = uri("$mirrorRoot/google")
                content {
                    includeGroupByRegex("com\\.android(\\..*)?")
                    includeGroupByRegex("androidx\\..*")
                    includeGroupByRegex("com\\.google\\.android(\\..*)?")
                }
            }
            maven {
                url = uri("$mirrorRoot/maven")
            }
        } else {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "Mora"
include(":app")
