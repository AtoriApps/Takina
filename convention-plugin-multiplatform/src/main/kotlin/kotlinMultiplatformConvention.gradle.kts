val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(
        jdkVersion = libs.findVersion("java-languageVersion").get().requiredVersion.toInt()
    )

    jvm {
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }

    // TODO：重开Js库
    /*js(IR) {
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
    }*/

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
            }
        }

        commonMain.dependencies {
            implementation(kotlin("stdlib-common"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
        }

        // TODO：重开Js库
        /*jsMain.dependencies {
            implementation(kotlin("stdlib-js"))
        }
        jsTest.dependencies {
            implementation(kotlin("test-js"))
        }*/
    }
}
