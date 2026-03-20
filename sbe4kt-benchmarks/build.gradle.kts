plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("sbe4kt")
    id("com.gradleup.shadow") version "9.3.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":sbe4kt-lib"))
    ksp(project(":sbe4kt-ksp"))

    implementation("uk.co.real-logic:sbe-all:1.37.1")
    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

kotlin {
    jvmToolchain(26)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin", layout.buildDirectory.dir("generated/ksp/main/kotlin")) }
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "org.openjdk.jmh.Main"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register<JavaExec>("run") {
    dependsOn(tasks.shadowJar)
    classpath = files(tasks.shadowJar.flatMap { it.archiveFile })
    mainClass.set("org.openjdk.jmh.Main")
    jvmArgs(
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
        "-Dagrona.disable.bounds.checks=true",
    )
    args("-w", "1s", "-r", "1s", "-wi", "3", "-i", "5", "-f", "5", "-tu", "ns")
}
