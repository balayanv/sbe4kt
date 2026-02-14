package sbe4kt.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test

class Sbe4KtPlugin : Plugin<Project> {

   companion object {
      private const val SBE_COMPILER_MAIN = "uk.co.real_logic.sbe.SbeTool"
      private const val JVM_UNSAFE_ACCESS = "--sun-misc-unsafe-memory-access=allow"
      private const val JVM_ADD_EXPORTS = "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED"
      private const val SBE_ALL = "uk.co.real-logic:sbe-all:1.37.1"
   }

   override fun apply(project: Project) {
      val sbeCompiler = project.configurations.create("sbeCompiler")
      project.dependencies.add("sbeCompiler", SBE_ALL)

      project.afterEvaluate {
         val sourceSets = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
         sourceSets.getByName("main") { ss ->
            ss.java.srcDirs(project.layout.buildDirectory.dir("generated/sbe/main/java"))
         }
         sourceSets.getByName("test") { ss ->
            ss.java.srcDirs(project.layout.buildDirectory.dir("generated/sbe/test/java"))
         }
      }

      val sbeMain = createSbeTask(project, "sbeGenerateMain", "main", "kspKotlin", sbeCompiler, true)
      val sbeTest = createSbeTask(project, "sbeGenerateTest", "test", "kspTestKotlin", sbeCompiler, false)

      project.tasks.named("compileJava") { it.dependsOn(sbeMain) }
      project.tasks.named("compileKotlin") { it.dependsOn(sbeMain) }
      project.tasks.named("compileTestJava") { it.dependsOn(sbeTest) }
      project.tasks.named("compileTestKotlin") { it.dependsOn(sbeTest) }

      project.tasks.withType(Test::class.java).configureEach {
         it.useJUnitPlatform()
         it.jvmArgs(JVM_UNSAFE_ACCESS, JVM_ADD_EXPORTS)
      }

      project.tasks.named("clean") { it.doLast { project.delete("build/generated/sbe") } }
      project.tasks.withType(Tar::class.java).configureEach { it.duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
      project.tasks.withType(Zip::class.java).configureEach { it.duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
   }

   private fun createSbeTask(
      project: Project,
      taskName: String,
      sourceSet: String,
      kspDep: String,
      sbeCompiler: Configuration,
      packageInfo: Boolean,
   ): TaskProvider<JavaExec> {
      return project.tasks.register(taskName, JavaExec::class.java) { task ->
         task.group = "build"
         task.description = "Generate SBE Java code from $sourceSet XML schema"
         task.dependsOn(kspDep)
         task.mainClass.set(SBE_COMPILER_MAIN)
         task.classpath = sbeCompiler
         task.jvmArgs(JVM_UNSAFE_ACCESS, JVM_ADD_EXPORTS)

         val schema = project.file("build/generated/ksp/$sourceSet/resources/sbe-schema.xml")
         val output = project.file("build/generated/sbe/$sourceSet/java")

         task.inputs.files(schema)
         task.outputs.dir(output)

         task.doFirst {
            output.mkdirs()
            if (schema.exists()) {
               task.systemProperty("sbe.output.dir", output.absolutePath)
               task.systemProperty("sbe.generate.ir", "true")
               task.systemProperty("sbe.target.language", "Java")
               task.systemProperty("sbe.java.generate.package.info", packageInfo.toString())
               task.args = listOf(schema.absolutePath)
            } else {
               throw StopExecutionException("$sourceSet SBE schema not found at ${schema.absolutePath}")
            }
         }

         task.onlyIf { schema.exists() }
      }
   }
}
