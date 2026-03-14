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

   val flatExpansion = if (hasComposites) {
      val compositeFields = message.fields.filter { it.type is Composite }.sortedBy { it.sbeOrder }
      val branches = compositeFields.joinToString("\n") {
         val offset = it.offset + 8
         "\"${it.name}\" -> result.addAll(${it.name}.sbeLayout().map { r -> r.copy(offset = r.offset + $offset) })"
      }
      "fun $className.printSbeLayout(flat: Boolean = false) {\n" +
      "   if (flat) {\n" +
      "      val result = mutableListOf<SbeLayoutRow>()\n" +
      "      for (row in sbeLayout()) {\n" +
      "         if (row.metadata == \"composite\") {\n" +
      "            result.add(SbeLayoutRow(0, \"\", \"\", \"\", 0, \"\", metadata = \"separator\"))\n" +
      "            when (row.field) {\n" +
      branches.indent(15) + "\n" +
      "            }\n" +
      "         } else {\n" +
      "            result.add(row)\n" +
      "         }\n" +
      "      }\n" +
      "      result.toCSV().print()\n" +
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
      |$$flatExpansion
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
      val metadata = if (fieldIr.type is Composite) """, metadata = "composite"""" else ""
      val value = if (fieldIr.type.name == "String") field else "$field.toString()"
      result.add("""SbeLayoutRow($offset, "$field", "$type", "$sbeType", $size, $value$metadata),""")
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
