package sbe4kt.kt

import sbe4kt.ir.Composite
import sbe4kt.ir.RepeatingGroup
import sbe4kt.ir.Message
import sbe4kt.ir.Struct
import sbe4kt.ir.VarStringN
import sbe4kt.util.indent

internal fun generateUtilsSection(message: Message): String {
   val className = message.name
   val hasComposites = message.fields.any { it.type is Composite }
   val hasGroups = message.fields.any { it.type is RepeatingGroup }
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
   val needsFlatMode = hasComposites || hasGroups
   val printMethod = if (needsFlatMode) {
      val flatBranches = generateFlatBranches(message)
      "fun $className.printSbeLayout(flat: Boolean = false) {\n" +
      "   if (flat) {\n" +
      "      val result = mutableListOf<SbeLayoutRow>()\n" +
      "      for (row in sbeLayout()) {\n" +
      "         when {\n" +
      "            row.metadata == \"root\" || row.sbeType == \"group\" -> {\n" +
      "               result.add(SbeLayoutRow(0, \"\", \"\", \"\", 0, \"\", metadata = \"separator\"))\n" +
      "               result.add(row)\n" +
      "               result.add(SbeLayoutRow(0, \"\", \"\", \"\", 0, \"\", metadata = \"separator\"))\n" +
      "               when (row.field) {\n" +
      flatBranches.indent(18) + "\n" +
      "               }\n" +
      "               result.add(SbeLayoutRow(0, \"\", \"\", \"\", 0, \"\", metadata = \"separator\"))\n" +
      "            }\n" +
      "            else -> result.add(row)\n" +
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

private fun generateFlatBranches(message: Message): String {
   val branches = mutableListOf<String>()

   // Composite branches
   val compositeFields = message.fields.filter { it.type is Composite }.sortedBy { it.sbeOrder }
   for (field in compositeFields) {
      branches.add(
         "\"${field.name}\" -> {\n" +
         "   result.addAll(${field.name}.sbeLayout(startOffset = row.offset))\n" +
         "}"
      )
   }

   // Group branches
   val groupFields = message.fields.filter { it.type is RepeatingGroup }.sortedBy { it.sbeOrder }
   for (field in groupFields) {
      val elemType = (field.type as RepeatingGroup).elementType
      val elemTypeName = elemType.name
      val elemSbeType = elemType.sbeName
      val name = field.name

      val body = when (elemType) {
         is Struct -> buildString {
            appendLine("var elemOffset = row.offset + 4")
            appendLine("for ((i, item) in $name.withIndex()) {")
            appendLine("   result.add(SbeLayoutRow(elemOffset, \"$name[\$i]\", \"$elemTypeName\", \"$elemSbeType\", ${elemType.encodedLength}, item.toString()))")
            appendLine("   elemOffset += ${elemType.encodedLength}")
            append("}")
         }
         is VarStringN -> buildString {
            appendLine("var elemOffset = row.offset + 4")
            appendLine("for ((i, item) in $name.withIndex()) {")
            appendLine("   result.add(SbeLayoutRow(elemOffset, \"$name[\$i]\", \"String\", \"$elemSbeType\", 4 + item.length, item))")
            appendLine("   elemOffset += 4 + item.length")
            append("}")
         }
         else -> buildString {
            appendLine("var elemOffset = row.offset + 4")
            appendLine("for ((i, item) in $name.withIndex()) {")
            appendLine("   result.add(SbeLayoutRow(elemOffset, \"$name[\$i]\", \"$elemTypeName\", \"$elemSbeType\", ${elemType.size}, item.toString()))")
            appendLine("   elemOffset += ${elemType.size}")
            append("}")
         }
      }

      branches.add("\"$name\" -> {\n${body.indent(3)}\n}")
   }

   return branches.joinToString("\n")
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
            val elemType = fieldIr.type.elementType
            val field = fieldIr.name
            val typeName = fieldIr.type.name
            val sizeExpr = when (elemType) {
               is Struct -> "4 + count * ${elemType.encodedLength}"
               is VarStringN -> "4 + $field.sumOf { 4 + it.length }"
               else -> "4 + count * ${elemType.size}"
            }
            "run {\n" +
            "   val count = $field.size\n" +
            "   val groupSize = $sizeExpr\n" +
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
