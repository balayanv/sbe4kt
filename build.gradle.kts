import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

val jvmUnsafeAccessArg = "--sun-misc-unsafe-memory-access=allow"
val jvmAddExportsArg = "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"

plugins {
   kotlin("jvm") version "2.3.10"
   id("com.google.devtools.ksp") version "2.3.6"
   id("sbe4kt")
   id("com.github.ben-manes.versions") version "0.53.0"
   application
   idea
}

kotlin {
   jvmToolchain(25)
   compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
   }
}

java {
   sourceCompatibility = JavaVersion.VERSION_17
   targetCompatibility = JavaVersion.VERSION_17
}

ksp {
   arg("kspLogging", "true")
   arg("ksp.incremental", "false")
   arg("ksp.enable.debug", "true")
}

repositories {
   mavenCentral()
}

dependencies {
   ksp(project(":sbe4kt-ksp"))
   implementation(project(":sbe4kt-lib"))

   implementation("uk.co.real-logic:sbe-all:1.37.1")

   testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
   testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
   mainClass.set("example.ExampleKt")
   applicationDefaultJvmArgs = listOf(jvmUnsafeAccessArg, jvmAddExportsArg)
}

sourceSets {
   main {
      kotlin { srcDirs("src/main/kotlin", layout.buildDirectory.dir("generated/ksp/main/kotlin")) }
   }
   test {
      kotlin { srcDirs("src/test/kotlin", layout.buildDirectory.dir("generated/ksp/test/kotlin")) }
   }
}

tasks.withType<DependencyUpdatesTask> {
   rejectVersionIf {
      val v = candidate.version.uppercase()
      listOf("-ALPHA", "-BETA", "-RC", "-M", ".M", "-DEV", "-SNAPSHOT").any { v.contains(it) }
   }
}
