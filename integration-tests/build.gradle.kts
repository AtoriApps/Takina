
plugins {
	kotlin("multiplatform")
	alias(libs.plugins.kotlinx.serialization)
}


kotlin {
	jvmToolchain(jdkVersion = 21)
	jvm {
		withJava()
		testRuns["test"].executionTask.configure {
			useJUnit()
		}
	}

	sourceSets {
		all {
			languageSettings {
				optIn("kotlin.RequiresOptIn")
			}
		}
		named("jvmTest") {
			dependencies {
				implementation(kotlin("test"))
				implementation(kotlin("test-junit"))
				implementation(project(":takina-core"))
				implementation(libs.kotlinx.datetime)
			}
		}
	}
}