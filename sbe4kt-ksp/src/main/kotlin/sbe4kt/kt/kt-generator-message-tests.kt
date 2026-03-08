package sbe4kt.kt

import com.google.devtools.ksp.processing.Dependencies
import sbe4kt.KSP
import sbe4kt.ir.ByteArrayN
import sbe4kt.ir.CharArrayN
import sbe4kt.ir.DoubleArrayN
import sbe4kt.ir.FloatArrayN
import sbe4kt.ir.IntArrayN
import sbe4kt.ir.LongArrayN
import sbe4kt.ir.ShortArrayN
import sbe4kt.ir.Message
import sbe4kt.ir.ArrayN
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate

internal fun generateTestsSection(message: Message): String {
   val className = message.name
   val codecName = "${className}Codec"

   val hasUnsupportedFields = message.fields.any { !isSupportedCodecType(it.type) }
   if (hasUnsupportedFields) return "// Tests skipped: $className contains unsupported field types"

   val hasArrayFields = message.fields.any { it.type is ByteArrayN || it.type is ShortArrayN || it.type is IntArrayN || it.type is LongArrayN || it.type is FloatArrayN || it.type is DoubleArrayN || it.type is CharArrayN || it.type is ArrayN }
   val assertFn = if (hasArrayFields) "assertFieldsEqual" else "assertEquals"

   val assertHelper = if (hasArrayFields) {
      val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }
      val assertions = fieldsDecOrder.joinToString("\n") { field ->
         if (field.type is ArrayN) {
            "assertArrayEquals(expected.${field.name}, actual.${field.name}, \"${field.name}\")"
         } else if (field.type is ByteArrayN || field.type is ShortArrayN || field.type is IntArrayN || field.type is LongArrayN || field.type is FloatArrayN || field.type is DoubleArrayN || field.type is CharArrayN) {
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
      |      val fw = ${className}FW().init()
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
      |
      |   @Test
      |   fun testCodecCorrectness() {
      |      val c2cBuffer = UnsafeBuffer(ByteArray($codecName.SIZE))
      |      val s2sBuffer = UnsafeBuffer(ByteArray($codecName.SIZE))
      |      val c2sBuffer = UnsafeBuffer(ByteArray($codecName.SIZE))
      |      val s2cBuffer = UnsafeBuffer(ByteArray($codecName.SIZE))
      |      val fw = ${className}FW()
      |
      |      repeat(100) {
      |         val original = random$className()
      |         val c2c = new$className()
      |         fw.wrap(c2cBuffer).encodeFrom(original).decodeInto(c2c)
      |
      |         val s2s = new$className()
      |         encode${className}SBE(original, s2sBuffer, 0)
      |         decode${className}SBE(s2s, s2sBuffer, 0)
      |
      |         val c2s = new$className()
      |         fw.wrap(c2sBuffer).encodeFrom(original)
      |         decode${className}SBE(c2s, c2sBuffer, 0)
      |
      |         val s2c = new$className()
      |         encode${className}SBE(original, s2cBuffer, 0)
      |         fw.wrap(s2cBuffer).decodeInto(s2c)
      |
      |         assertEquals(c2cBuffer, s2sBuffer)
      |         assertEquals(s2sBuffer, c2sBuffer)
      |         assertEquals(c2sBuffer, s2cBuffer)
      |
      |         $assertFn(original, c2c)
      |         $assertFn(original, s2s)
      |         $assertFn(original, c2s)
      |         $assertFn(original, s2c)
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
   val hasArrayFields = message.fields.any { it.type is ByteArrayN || it.type is ShortArrayN || it.type is IntArrayN || it.type is LongArrayN || it.type is FloatArrayN || it.type is DoubleArrayN || it.type is CharArrayN || it.type is ArrayN }
   val testImports = mutableSetOf(
      "org.agrona.concurrent.UnsafeBuffer",
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
