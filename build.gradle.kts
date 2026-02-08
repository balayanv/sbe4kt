val jvmUnsafeAccessArg = "--sun-misc-unsafe-memory-access=allow"
val jvmAddExportsArg = "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED"
val sbeCompilerMain = "uk.co.real_logic.sbe.SbeTool"
val kotlinVersion = "2.3.0"
val kspVersion = "2.3.5"

plugins {
   kotlin("jvm") version "2.3.0"
   id("com.google.devtools.ksp") version "2.3.5"
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

configurations {
   create("sbeCompiler")
}

dependencies {
   // KSP and SBE4KT
   ksp(project(":sbe4kt"))
   implementation(project(":sbe4kt"))

   // External dependencies
   implementation("org.agrona:agrona:2.4.0")
   implementation("uk.co.real-logic:sbe-all:1.37.1")
   "sbeCompiler"("uk.co.real-logic:sbe-all:1.37.1")

   // Testing
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
      java { srcDirs("src/main/java", layout.buildDirectory.dir("generated/sbe/main/java")) }
   }
   test {
      kotlin { srcDirs("src/test/kotlin", layout.buildDirectory.dir("generated/ksp/test/kotlin")) }
      java { srcDirs("src/test/java", layout.buildDirectory.dir("generated/sbe/test/java")) }
   }
}


fun createSbeGenerateTask(
   taskName: String,
   sourceSet: String,
   kspDependency: String,
   generatePackageInfo: Boolean = true
): TaskProvider<JavaExec> {
   return tasks.register<JavaExec>(taskName) {
      group = "build"
      description = "Generate SBE Java code from $sourceSet XML schema"

      dependsOn(kspDependency)

      mainClass.set(sbeCompilerMain)
      classpath = configurations["sbeCompiler"]
      jvmArgs(jvmUnsafeAccessArg, jvmAddExportsArg)

      val schemaFile = file("build/generated/ksp/$sourceSet/resources/sbe-schema.xml")
      val outputDir = file("build/generated/sbe/$sourceSet/java")

      inputs.files(schemaFile)
      outputs.dir(outputDir)

      doFirst {
         outputDir.mkdirs()
         if (schemaFile.exists()) {
            systemProperty("sbe.output.dir", outputDir.absolutePath)
            systemProperty("sbe.generate.ir", "true")
            systemProperty("sbe.target.language", "Java")
            systemProperty("sbe.java.generate.package.info", generatePackageInfo.toString())
            args = listOf(schemaFile.absolutePath)
         } else {
            println("$sourceSet SBE schema not found at ${schemaFile.absolutePath}, skipping SBE generation")
            throw StopExecutionException("Schema file not found")
         }
      }

      onlyIf {
         schemaFile.exists()
      }
   }
}

val sbeGenerateMain = createSbeGenerateTask(
   taskName = "sbeGenerateMain",
   sourceSet = "main",
   kspDependency = "kspKotlin",
   generatePackageInfo = true
)

val sbeGenerateTest = createSbeGenerateTask(
   taskName = "sbeGenerateTest",
   sourceSet = "test",
   kspDependency = "kspTestKotlin",
   generatePackageInfo = false
)

tasks.named("clean") {
   doLast {
      delete("build/generated/sbe")
   }
}

tasks.test {
   useJUnitPlatform()
   jvmArgs(jvmUnsafeAccessArg, jvmAddExportsArg)
}

tasks.named("compileJava") { dependsOn(sbeGenerateMain) }
tasks.named("compileKotlin") { dependsOn(sbeGenerateMain) }

tasks.named("compileTestJava") { dependsOn(sbeGenerateTest) }
tasks.named("compileTestKotlin") { dependsOn(sbeGenerateTest) }

tasks.withType<Tar> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
tasks.withType<Zip> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }