package sbe4kt.kt

import com.google.devtools.ksp.processing.Dependencies
import sbe4kt.KSP
import sbe4kt.ir.Message
import sbe4kt.ir.ArrayN
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate

internal fun generateTestsSection(message: Message): String {
   val className = message.name

   val hasUnsupportedFields = message.fields.any { !isSupportedCodecType(it.type) }
   if (hasUnsupportedFields) return "// Tests skipped: $className contains unsupported field types"

   val hasArrayFields = message.fields.any { it.type is ArrayN }
   val assertFn = if (hasArrayFields) "assertFieldsEqual" else "assertEquals"

   val assertHelper = if (hasArrayFields) {
      val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }
      val assertions = fieldsDecOrder.joinToString("\n") { field ->
         if (field.type is ArrayN) {
            "assertArrayEquals(expected.${field.name}, actual.${field.name}, \"${field.name}\")"
         } else {
            "assertEquals(expected.${field.name}, actual.${field.name}, \"${field.name}\")"
         }
      }
      """
      |
      |   private fun assertFieldsEqual(expected: $className, actual: $className) {
      |${assertions.indent(6)}
      |   }""".trimTemplate()
   } else ""

   return """
      |internal class ${className}Test {
      |$assertHelper
      |   @Test
      |   fun testFlyweightRoundTrip() {
      |      val fw = ${className}FW()
      |      repeat(100) {
      |         val original = random$className()
      |         fw.encodeFrom(original)
      |
      |         val decoded = new$className()
      |         fw.decodeInto(decoded)
      |
      |         $assertFn(original, decoded)
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
   val hasArrayFields = message.fields.any { it.type is ArrayN }
   val testImports = mutableSetOf(
      "org.junit.jupiter.api.Test",
      "org.junit.jupiter.api.Assertions.assertEquals",
   )
   if (hasArrayFields) {
      testImports.add("org.junit.jupiter.api.Assertions.assertArrayEquals")
   }

   val allImports = (message.allImports() + testImports).sorted()
   val importsSection = allImports.joinToString("\n") { "import $it" }

   // Generate the test section
   val testsSection = generateTestsSection(message)

   // Write the complete file
   file.bufferedWriter().use {
      it.write("""
         |package $packageName
         |
         |$importsSection
         |
         |$testsSection
         |""".trimTemplate()
      )
   }
}
