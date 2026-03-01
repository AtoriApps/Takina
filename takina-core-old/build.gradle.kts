plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "org.atoriapps.takina.core"
version = "0.0.1"

kotlin {
    jvm()
    // js等其余主平台

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.signal.protocol)
            }
        }
    }
}
