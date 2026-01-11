plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.4")
    implementation("uk.co.real-logic:sbe-tool:1.30.0")
}

kotlin {
    jvmToolchain(25)
}