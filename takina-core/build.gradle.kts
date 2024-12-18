plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.jetbrains.dokka)
}

kotlin {
    jvmToolchain(jdkVersion = 21)

    jvm {
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }

    /*
    js(IR) {
        browser {
            commonWebpackConfig {
                // cssSupport()
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
            binaries.executable()
        }
    }
    */

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
            }
        }

        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.krypto)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val omemoMain by creating {
            dependsOn(commonMain)
        }

        val omemoTest by creating {
            dependsOn(commonTest)
        }

        val jvmMain by getting {
            dependsOn(omemoMain)
            dependencies {
                implementation(libs.minidns)
                implementation(libs.signal.protocol.java)
            }
        }

        val jvmTest by getting {
            dependsOn(omemoTest)
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }
}

// TODO: 重开下载libsignal和OpenSSL
/*tasks.register("prepareLibsignal") {
	description = "Download and unpack libsignal XCFramework."
	val zipUrl = "https://github.com/tigase/libsignal/releases/download/1.0.0/libsignal.xcframework.zip"

	fun download(url: String, path: String) = ant.invokeMethod("get", mapOf("src" to url, "dest" to File(path)))

	doLast {
		if (!File("$rootDir/frameworks/libsignal.xcframework.zip").exists()) {
			logger.lifecycle("Downloading libsignal framework...")
			download(
				zipUrl, "$rootDir/frameworks/"
			)
		}
		if (!File("$rootDir/frameworks/libsignal.xcframework").exists()) {
			logger.lifecycle("Unzipping libsignal framework...")
			copy {
				from(zipTree("$rootDir/frameworks/libsignal.xcframework.zip"))
				into("$rootDir/frameworks/")
			}
		}
	}
}

tasks.register("prepareOpenSSL") {
	description = "Downloads and unpack OpenSSL XCFramework."
	val zipUrl = "https://github.com/tigase/openssl-swiftpm/releases/download/1.1.171/OpenSSL.xcframework.zip"

	fun download(url: String, path: String) = ant.invokeMethod("get", mapOf("src" to url, "dest" to File(path)))

	doLast {
		if (!File("$rootDir/frameworks/OpenSSL.xcframework.zip").exists()) {
			logger.lifecycle("Downloading OpenSSL framework...")
			download(
				zipUrl, "$rootDir/frameworks/"
			)
		}
		if (!File("$rootDir/frameworks/OpenSSL.xcframework").exists()) {
			logger.lifecycle("Unzipping OpenSSL framework...")
			copy {
				from(zipTree("$rootDir/frameworks/OpenSSL.xcframework.zip"))
				into("$rootDir/frameworks/")
			}
		}
	}
}*/

// TODO: 重开这玩意儿：生成文档的
/*tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    moduleName.set("Takina by Atori Apps")
    moduleVersion.set(project.version.toString())
    failOnWarning.set(false)
    suppressObviousFunctions.set(true)
    suppressInheritedMembers.set(false)
    offlineMode.set(false)
}*/
