import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "sbe4kt"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":sbe4kt-lib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.5")
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