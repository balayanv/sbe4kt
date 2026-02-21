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
    api("org.agrona:agrona:2.4.0")
}

kotlin {
    jvmToolchain(25)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "sbe4kt-lib"
            from(components["java"])
        }
    }
}
