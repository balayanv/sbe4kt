val jvmUnsafeAccessArg = "--sun-misc-unsafe-memory-access=allow"
val jvmAddExportsArg = "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED"

plugins {
   kotlin("jvm") version "2.3.0"
   id("com.google.devtools.ksp") version "2.3.5"
   id("sbe4kt")
   id("com.github.ben-manes.versions") version "0.53.0"
   application
   idea
}

kotlin {
   jvmToolchain(25)
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

   testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
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
