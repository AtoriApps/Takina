import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // alias(libs.plugins.vanniktech.mavenPublish)
}

group = "org.atoriapps.takina.core"
version = "0.0.1"

kotlin {
    jvm()
    // js()

    sourceSets {
        val commonMain by getting {
            dependencies {
                //put your multiplatform dependencies here
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines)
            }
        }
    }
}

// TODO：Maven
/*mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(group.toString(), "library", version.toString())

    pom {
        name = "Takina"
        description = "A Kotlin Multiplatform XMPP Client Library."
        inceptionYear = "2024"
        url = "https://github.com/AtoriApps/Takina"
        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "atoriapps"
                name = "Atori Apps"
                url = "https://github.com/AtoriApps/"
            }
        }
    }
}*/
