
plugins {
	id("kotlinMultiplatformConvention")
	`maven-publish`
	signing
	alias(libs.plugins.kotlinx.serialization)
}


kotlin {

	sourceSets {
		named("jvmMain") {
			dependencies {
				implementation(project(":takina-core"))
				implementation(libs.bouncycastle)
			}
		}
	}
}