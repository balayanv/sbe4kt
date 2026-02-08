plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.5")
    implementation("uk.co.real-logic:sbe-tool:1.37.1")
}

kotlin {
    jvmToolchain(25)
}