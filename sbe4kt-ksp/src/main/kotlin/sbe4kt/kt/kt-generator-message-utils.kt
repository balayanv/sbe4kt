package sbe4kt.kt

import sbe4kt.ir.Composite
import sbe4kt.ir.Message
import sbe4kt.util.indent
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateUtilsSection(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()
   val rows = generateLayoutRows(message)
   val hasComposites = message.fields.any { it.type is Composite }

   val headerValue = "\${${className}Encoder.BLOCK_LENGTH}|\${${className}Encoder.TEMPLATE_ID}|\${${className}Encoder.SCHEMA_ID}|\${${className}Encoder.SCHEMA_VERSION}"

   val printMethod = if (hasComposites) {
      val flatRows = generateFlatLayoutRows(message)
      "fun $className.printSbeLayout(flat: Boolean = false) {\n" +
      "   if (flat) {\n" +
      "      val rows = listOf(\n" +
      "         SbeLayoutRow(0, \"[HEADER]\", \"Header\", \"\", 8, \"$headerValue\"),\n" +
      flatRows.indent(9) + "\n" +
      "      )\n" +
      "      rows.toCSV().print()\n" +
      "   } else {\n" +
      "      sbeLayout().toCSV().print()\n" +
      "   }\n" +
      "}"
   } else {
      "fun $className.printSbeLayout(flat: Boolean = false) {\n" +
      "   sbeLayout().toCSV().print()\n" +
      "}"
   }

   return $$"""
      |internal fun $$className.sbeLayout() : List<SbeLayoutRow> {
      |   return listOf(
      |      SbeLayoutRow(0, "[HEADER]", "Header", "", 8, "${$${className}Encoder.BLOCK_LENGTH}|${$${className}Encoder.TEMPLATE_ID}|${$${className}Encoder.SCHEMA_ID}|${$${className}Encoder.SCHEMA_VERSION}"),
      |$${rows.indent(6)}
      |   )
      |}
      |
      |$$printMethod
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

private fun generateFlatLayoutRows(message: Message): String {
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

      if (fieldIr.type is Composite) {
         val offset = fieldIr.offset + headerSize
         result.add("""SbeLayoutRow(0, "", "", "", 0, "", metadata = "separator"),""")
         result.add("""*${fieldIr.name}.sbeLayout().map { it.copy(offset = it.offset + $offset) }.toTypedArray(),""")
      } else {
         val offset = fieldIr.offset + headerSize
         val field = fieldIr.name
         val type = fieldIr.type.name
         val sbeType = fieldIr.type.sbeName
         val size = fieldIr.type.size
         val value = if (fieldIr.type.name == "String") field else "$field.toString()"
         result.add("""SbeLayoutRow($offset, "$field", "$type", "$sbeType", $size, $value),""")
      }
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
