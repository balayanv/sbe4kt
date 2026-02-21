plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "sbe4kt"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api("org.agrona:agrona:2.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "sbe4kt-lib"
            from(components["java"])
        }
    }
}
