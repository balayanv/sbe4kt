package sbe4kt.kt

import com.google.devtools.ksp.processing.Dependencies
import sbe4kt.KSP
import sbe4kt.ir.Message
import sbe4kt.util.trimTemplate

internal fun generateTestsSection(message: Message): String {
   val className = message.name
   val codecName = "${className}Codec"

   val hasUnsupportedFields = message.fields.any { !isSupportedCodecType(it.type) }
   if (hasUnsupportedFields) return "// Tests skipped: $className contains unsupported field types"

   return """
      |internal class ${className}Test {
      |
      |   @Test
      |   fun testCodecRoundTrip() {
      |      val buffer = UnsafeBuffer(ByteArray($codecName.SIZE))
      |      repeat(100) {
      |         val original = random$className()
      |         encode$className(original, buffer)
      |
      |         val decoded = new$className()
      |         decode$className(decoded, buffer)
      |
      |         assertEquals(original, decoded)
      |      }
      |   }
      |
      |   @Test
      |   fun testFlyweightRoundTrip() {
      |      val rw = ${className}RW()
      |      repeat(100) {
      |         val original = random$className()
      |         rw.encodeFrom(original)
      |
      |         val decoded = new$className()
      |         rw.decodeInto(decoded)
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
   )

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
