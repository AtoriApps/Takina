
plugins {
	kotlin("multiplatform")
	kotlin("plugin.serialization")
}


kotlin {
	jvmToolchain(jdkVersion = libs.versions.java.languageVersion.get().toInt())
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
				implementation(project(":takina-bouncycastle"))
				implementation(deps.kotlinx.datetime)
			}
		}
	}
}