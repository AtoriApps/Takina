
rootProject.name = "takina"

include(
	":docs",
	":takina-bouncycastle",
	":takina-core",
	":takina-coroutines",
	":takina-rx",
)

pluginManagement {
	includeBuild("convention-plugin-multiplatform")
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
