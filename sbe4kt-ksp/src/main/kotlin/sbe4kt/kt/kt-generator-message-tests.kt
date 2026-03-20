package sbe4kt.kt

import com.google.devtools.ksp.processing.Dependencies
import sbe4kt.KSP
import sbe4kt.ir.Message
import sbe4kt.ir.StringN
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateTestsSection(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()
   val bufferExpr = if (message.hasVariableFields) "ExpandableArrayBuffer()" else "UnsafeBuffer(ByteArray($sizeConstName))"
   // StringBuilder doesn't implement value equality — compare toString() instead
   val hasStringBuilder = message.fields.any { it.type is StringN }
   val equalityCheck = if (hasStringBuilder) {
      "assertEquals(original.toString(), decoded.toString())"
   } else {
      "assertEquals(original, decoded)"
   }

   return """
      |internal class ${className}Test {
      |
      |   @Test
      |   fun testRandomValues() {
      |      val buffer = $bufferExpr
      |      repeat(100) {
      |         val original = random$className()
      |         val bytesWritten = original.encodeTo(buffer)
      |
      |         val decoded = new$className()
      |         val bytesRead = decoded.decodeFrom(buffer)
      |
      |         assertTrue(bytesWritten > 0)
      |         assertEquals(bytesWritten, bytesRead)
      |         $equalityCheck
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
      "org.agrona.ExpandableArrayBuffer",
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
