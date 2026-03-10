plugins {
    kotlin("jvm") version "2.3.10"
    `java-gradle-plugin`
    `maven-publish`
}

group = "sbe4kt"
version = "0.1.3-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
}

kotlin {
    jvmToolchain(25)
}

gradlePlugin {
    plugins {
        create("sbe4kt") {
            id = "sbe4kt"
            implementationClass = "sbe4kt.gradle.Sbe4KtPlugin"
        }
    }
}
