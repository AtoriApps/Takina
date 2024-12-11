
plugins {
	id("kotlinMultiplatformConvention")
	`maven-publish`
	signing
	alias(libs.plugins.kotlinx.serialization)
}


kotlin {

	sourceSets {
		named("commonMain") {
			dependencies {
				implementation(project(":takina-core"))
				implementation(libs.reactive.reaktive)
			}
		}
		named("commonTest") {
			dependencies {
				implementation(libs.reactive.testing)
			}
		}
	}
}