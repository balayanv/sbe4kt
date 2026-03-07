package sbe4kt.kt

import sbe4kt.ir.Message
import sbe4kt.util.indent
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateUtilsSection(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()
   val rows = generateLayoutRows(message)
   return $$"""
      |fun $$className.sbeLayout() : List<SbeLayoutRow> {
      |   return listOf(
      |      SbeLayoutRow(0, "[HEADER]", "Header", "", 8, "${$${className}Encoder.BLOCK_LENGTH}|${$${className}Encoder.TEMPLATE_ID}|${$${className}Encoder.SCHEMA_ID}|${$${className}Encoder.SCHEMA_VERSION}"),
      |$${rows.indent(6)}
      |      SbeLayoutRow($$sizeConstName, "[END]", "", "", 0, ""),
      |   )
      |}
      |
      |fun $$className.printSbeLayout() {
      |   sbeLayout().toCSV().print()
      |}
      |""".trimTemplate()
}

private fun generateLayoutRows(message: Message): String {
   val result = mutableListOf<String>()

   val sortedFields = message.fields.sortedBy { it.sbeOrder }
   val headerSize = 8

   for ((index, fieldIr) in sortedFields.withIndex()) {
      if (message.usePadding) {
         val expectedOffset = if (index == 0) 0 else sortedFields[index - 1].offset + sortedFields[index - 1].type.size
         val paddingSize = fieldIr.offset - expectedOffset
         if (paddingSize > 0) {
            val offset = expectedOffset + headerSize
            result.add("""SbeLayoutRow($offset, "[PADDING]", "", "", $paddingSize, ""),""")
         }
      }

      val offset = fieldIr.offset + headerSize
      val field = fieldIr.name
      val type = fieldIr.type.name
      val sbeType = fieldIr.type.sbeName
      val size = fieldIr.type.size
      val value = if (fieldIr.type.name == "String") field else "$field.toString()"
      result.add("""SbeLayoutRow($offset, "$field", "$type", "$sbeType", $size, $value),""")
   }

   if (message.usePadding && sortedFields.isNotEmpty()) {
      val lastField = sortedFields.last()
      val finalPadding = message.blockLength - (lastField.offset + lastField.type.size)
      if (finalPadding > 0) {
         val offset = lastField.offset + lastField.type.size + headerSize
         result.add("""SbeLayoutRow($offset, "[PADDING]", "", "", $finalPadding, ""),""")
      }
   }

   return result.joinToString("\n")
}
