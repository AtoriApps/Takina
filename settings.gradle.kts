pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Takina"

include(
    ":takina-core-old",
    ":takina-examples-old",

    ":takina-core",
    ":takina-tests",
)
