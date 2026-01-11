plugins {
    kotlin("jvm") version "2.1.0"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    application
}

kotlin {
    jvmToolchain(23)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":sbe4kt"))
    ksp(project(":sbe4kt"))
    implementation("org.agrona:agrona:1.20.0")
}

ksp {
    arg("sbeOutputDir", layout.buildDirectory.dir("generated/sbe").get().asFile.absolutePath)
}

application {
    mainClass.set("MainKt")
}

sourceSets.main {
    java.srcDir("build/generated/sbe")
}

tasks.test {
    useJUnitPlatform()
}
