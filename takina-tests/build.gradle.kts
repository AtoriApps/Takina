plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "org.atoriapps.takina.tests"

kotlin {
    jvm()

    sourceSets {
        val jvmTest by getting {
            dependencies {
                implementation(project(":takina-core"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines)
                implementation(libs.junit4)
            }
        }
    }
}
