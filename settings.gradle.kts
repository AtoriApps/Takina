rootProject.name = "takina"

include(
	":docs",
	":takina-core",
	":integration-tests",
)

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositories {
		google()
		mavenCentral()
		mavenLocal()
	}
}
