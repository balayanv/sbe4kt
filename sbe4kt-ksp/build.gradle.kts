import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "sbe4kt"
version = "0.2.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":sbe4kt-lib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.6")
}

kotlin {
    jvmToolchain(26)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
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
