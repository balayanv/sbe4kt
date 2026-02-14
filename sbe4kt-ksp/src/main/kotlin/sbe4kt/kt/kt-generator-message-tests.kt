package sbe4kt.kt

import com.google.devtools.ksp.processing.Dependencies
import sbe4kt.KSP
import sbe4kt.ir.Message
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateTestsSection(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()

   return """
      |internal class ${className}Test {
      |
      |   @Test
      |   fun testRandomValues() {
      |      val buffer = UnsafeBuffer(ByteArray($sizeConstName))
      |      repeat(100) {
      |         val original = random$className()
      |         val bytesWritten = original.encodeTo(buffer)
      |         
      |         val decoded = new$className()
      |         val bytesRead = decoded.decodeFrom(buffer)
      |         
      |         assertTrue(bytesWritten > 0)
      |         assertEquals(bytesWritten, bytesRead)
      |         assertEquals(original, decoded)
      |      }
      |   }
      |
      |   @Test
      |   fun testFlyweights() {
      |      val wrapper = ${className}RW()
      |      repeat(100) {
      |         val original = random$className()
      |         wrapper.encodeFrom(original)
      |
      |         val decoded = new$className()
      |         wrapper.decodeInto(decoded)
      |
      |         assertEquals(original, decoded)
      |      }
      |   }
      |}
      |""".trimTemplate()
}

internal fun KSP.generateTestsForMessage(message: Message) {
   if (sourceSet != "test") return

   val packageName = message.packageStr
   val className = message.name
   val fileName = "${className}Test"

   val file = gen.createNewFile(
      Dependencies(true, *message.sourceFiles()),
      packageName,
      fileName
   )

   // Generate imports section
   val testImports = setOf(
      "org.agrona.concurrent.UnsafeBuffer",
      "org.junit.jupiter.api.Test",
      "org.junit.jupiter.api.Assertions.assertEquals",
      "org.junit.jupiter.api.Assertions.assertTrue"
   )

   val allImports = (message.allImports() + testImports).sorted()
   val importsSection = allImports.joinToString("\n") { "import $it" }

   // Generate the test section
   val testsSection = generateTestsSection(message)

   // Write the complete file
   file.bufferedWriter().use {
      it.write("""
         |@file:Suppress("unused")
         |
         |package $packageName
         |
         |$importsSection
         |
         |$testsSection
         |""".trimTemplate()
      )
   }
}
