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
