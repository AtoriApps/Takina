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
                implementation(project(":takina-core"))
                implementation(libs.kotlinx.coroutines)
            }
        }

        val jvmMain by getting
    }
}

tasks.register<JavaExec>("runBasicJvmExample") {
    group = "application"
    description = "Run the basic JVM Takina example"
    dependsOn("jvmJar")
    classpath(
        tasks.named("jvmJar"),
        kotlin.targets.getByName("jvm").compilations.getByName("main").runtimeDependencyFiles
    )
    mainClass.set("org.atoriapps.takina.examples.BasicJvmExampleKt")
}

tasks.register<JavaExec>("runSmokeClientExample") {
    group = "application"
    description = "Run the smoke XMPP client example (configure via TAKINA_* env vars)"
    dependsOn("jvmJar")
    classpath(
        tasks.named("jvmJar"),
        kotlin.targets.getByName("jvm").compilations.getByName("main").runtimeDependencyFiles
    )
    mainClass.set("org.atoriapps.takina.examples.SmokeClientExampleKt")
}

tasks.register<JavaExec>("runOmemoPrivateSmokeExample") {
    group = "application"
    description = "Run OMEMO private chat smoke example (requires TAKINA_A_*/TAKINA_B_* env vars)"
    dependsOn("jvmJar")
    classpath(
        tasks.named("jvmJar"),
        kotlin.targets.getByName("jvm").compilations.getByName("main").runtimeDependencyFiles
    )
    mainClass.set("org.atoriapps.takina.examples.OmemoPrivateSmokeExampleKt")
}
