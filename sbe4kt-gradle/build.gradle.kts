plugins {
    kotlin("jvm") version "2.3.10"
    `java-gradle-plugin`
    `maven-publish`
}

group = "sbe4kt"
version = "2.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
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

gradlePlugin {
    plugins {
        create("sbe4kt") {
            id = "sbe4kt"
            implementationClass = "sbe4kt.gradle.Sbe4KtPlugin"
        }
    }
}
