plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "org.atoriapps.takina.examples"
version = "0.0.1"

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":takina-core-old"))
                implementation(libs.kotlinx.coroutines)
            }
        }

        val jvmMain by getting
    }
}