
plugins {
	id("kotlinMultiplatformConvention")
	`maven-publish`
	signing
}

kotlin {

	sourceSets {
		named("commonMain") {
			dependencies {
				implementation(project(":takina-core"))
				implementation(libs.kotlinx.coroutines.core)
			}
		}
		named("commonTest") {
			dependencies {
				implementation(libs.kotlinx.coroutines.test)
			}
		}
	}
}