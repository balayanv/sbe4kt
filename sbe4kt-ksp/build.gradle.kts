import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "sbe4kt"
version = "0.1.3-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":sbe4kt-lib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.6")
}

kotlin {
    jvmToolchain(25)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "sbe4kt-ksp"
            from(components["java"])
        }
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xmulti-dollar-interpolation"))
}