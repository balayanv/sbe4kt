package sbe4kt.kt

import sbe4kt.ir.Composite
import sbe4kt.ir.RepeatingGroup
import sbe4kt.ir.Message
import sbe4kt.ir.Struct
import sbe4kt.util.indent

internal fun generateUtilsSection(message: Message): String {
   val className = message.name
   val hasComposites = message.fields.any { it.type is Composite }
   val hasVarFields = message.hasVariableFields
   val fixedRows = generateFixedLayoutRows(message)
   val varRows = generateVarLayoutRows(message)

   val headerValue = "\${${className}Encoder.BLOCK_LENGTH}|\${${className}Encoder.TEMPLATE_ID}|\${${className}Encoder.SCHEMA_ID}|\${${className}Encoder.SCHEMA_VERSION}"
   val fixedBlockEnd = message.blockLength + 8

   // --- sbeLayout ---
   val sbeLayoutMethod = if (hasVarFields) {
      "internal fun $className.sbeLayout(startOffset: Int = 0) : List<SbeLayoutRow> {\n" +
      "   val result = mutableListOf(\n" +
      "      SbeLayoutRow(startOffset + 0, \"[HEADER]\", \"Header\", \"\", 8, \"$headerValue\"),\n" +
      fixedRows.indent(6) + "\n" +
      "   )\n" +
      "   var varOffset = startOffset + $fixedBlockEnd\n" +
      varRows.indent(3) + "\n" +
      "   return result\n" +
      "}"
   } else {
      "internal fun $className.sbeLayout(startOffset: Int = 0) : List<SbeLayoutRow> {\n" +
      "   return listOf(\n" +
      "      SbeLayoutRow(startOffset + 0, \"[HEADER]\", \"Header\", \"\", 8, \"$headerValue\"),\n" +
      fixedRows.indent(6) + "\n" +
      "   )\n" +
      "}"
   }

   // --- printSbeLayout ---
   val printMethod = if (hasComposites) {
      val compositeFields = message.fields.filter { it.type is Composite }.sortedBy { it.sbeOrder }
      val branches = compositeFields.joinToString("\n") {
         "\"${it.name}\" -> {\n   result.addAll(${it.name}.sbeLayout(startOffset = row.offset))\n   result.add(SbeLayoutRow(0, \"\", \"\", \"\", 0, \"\", metadata = \"separator\"))\n}"
      }
      "fun $className.printSbeLayout(flat: Boolean = false) {\n" +
      "   if (flat) {\n" +
      "      val result = mutableListOf<SbeLayoutRow>()\n" +
      "      for (row in sbeLayout()) {\n" +
      "         if (row.metadata == \"root\") {\n" +
      "            result.add(SbeLayoutRow(0, \"\", \"\", \"\", 0, \"\", metadata = \"separator\"))\n" +
      "            result.add(row)\n" +
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

   return "$sbeLayoutMethod\n\n$printMethod\n"
}

private fun generateFixedLayoutRows(message: Message): String {
   val result = mutableListOf<String>()
   val sortedFields = message.fields.filter { it.type.size >= 0 }.sortedBy { it.sbeOrder }
   val headerSize = 8

   for ((index, fieldIr) in sortedFields.withIndex()) {
      if (message.usePadding) {
         val expectedOffset = if (index == 0) 0 else sortedFields[index - 1].offset + sortedFields[index - 1].type.size
         val paddingSize = fieldIr.offset - expectedOffset
         if (paddingSize > 0) {
            val offset = expectedOffset + headerSize
            result.add("""SbeLayoutRow(startOffset + $offset, "[PADDING]", "", "", $paddingSize, ""),""")
         }
      }

      val offset = fieldIr.offset + headerSize
      val field = fieldIr.name
      val type = fieldIr.type.name
      val sbeType = fieldIr.type.sbeName
      val size = fieldIr.type.size
      val metadata = if (fieldIr.type is Composite) """, metadata = "root"""" else ""
      val value = if (fieldIr.type.name == "String") field else "$field.toString()"
      result.add("""SbeLayoutRow(startOffset + $offset, "$field", "$type", "$sbeType", $size, $value$metadata),""")
   }

   if (message.usePadding && sortedFields.isNotEmpty()) {
      val lastField = sortedFields.last()
      val finalPadding = message.blockLength - (lastField.offset + lastField.type.size)
      if (finalPadding > 0) {
         val offset = lastField.offset + lastField.type.size + headerSize
         result.add("""SbeLayoutRow(startOffset + $offset, "[PADDING]", "", "", $finalPadding, ""),""")
      }
   }

   return result.joinToString("\n")
}

private fun generateVarLayoutRows(message: Message): String {
   val varFields = message.fields.filter { it.type.size < 0 }.sortedBy { it.sbeOrder }
   if (varFields.isEmpty()) return ""

   return varFields.mapIndexed { index, fieldIr ->
      val isLast = index == varFields.lastIndex
      when (fieldIr.type) {
         is RepeatingGroup -> {
            val struct = fieldIr.type.elementType as Struct
            val blockLen = struct.encodedLength
            val field = fieldIr.name
            val typeName = fieldIr.type.name
            "run {\n" +
            "   val count = $field.size\n" +
            "   val groupSize = 4 + count * $blockLen\n" +
            "   result.add(SbeLayoutRow(varOffset, \"$field\", \"$typeName\", \"group\", groupSize, \"count=\$count\"))\n" +
            if (!isLast) "   varOffset += groupSize\n}" else "}"
         }
         else -> {
            val field = fieldIr.name
            val type = fieldIr.type.name
            val sbeType = fieldIr.type.sbeName
            val value = if (fieldIr.type.name == "String") field else "$field.toString()"
            "result.add(SbeLayoutRow(varOffset, \"$field\", \"$type\", \"$sbeType\", $field.length, $value))" +
            if (!isLast) "\nvarOffset += $field.length" else ""
         }
      }
   }.joinToString("\n")
}
