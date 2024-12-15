import java.util.*

plugins {
    alias(libs.plugins.multiplatform).apply(false)
}

allprojects {
    group = "lib.takina"
    version = generateVersionName()
}

fun generateVersionName(): String {
    val ver = findProperty("version").toString()
    return ver
}

subprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        maven(url = findProperty("tigaseMavenRepoRelease").toString())
        maven(url = findProperty("tigaseMavenRepoSnapshot").toString())
    }
}