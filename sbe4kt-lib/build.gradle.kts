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
    api("org.agrona:agrona:2.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<Test> {
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
