package sbe4kt.kt

import sbe4kt.ir.Message
import sbe4kt.util.indent
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateUtilsSection(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()
   val rows = generateRows(message)
   return $$"""
      |fun $$className.printSbeLayout() {
      |   CSV()
      |      .info("$$className (totalSize = $$$sizeConstName bytes)")
      |      .addHeader("Offset", "Field", "Type", "SBE Type", "Size", "Value")
      |      .addRow("0", "[HEADER]", "Header", "", "${MessageHeaderEncoder.ENCODED_LENGTH}", "${$${className}Encoder.BLOCK_LENGTH}|${$${className}Encoder.TEMPLATE_ID}|${$${className}Encoder.SCHEMA_ID}|${$${className}Encoder.SCHEMA_VERSION}")
      |$${rows.indent(6)}
      |      .print()
      |}
      |""".trimTemplate()
}

private fun generateRows(message: Message): String {
   val result = mutableListOf<String>()

   fun addCsvRow(offset: Int, field: String, type: String, sbeType: String, size: String, value: String) {
      result.add(""".addRow("$offset", "$field", "$type", "$sbeType", "$size", $value)""")
   }

   val sortedFields = message.fields.sortedBy { it.sbeOrder }
   val headerSize = 8

   for ((index, fieldIr) in sortedFields.withIndex()) {
      // show padding before field if there is a gap
      if (message.usePadding) {
         val expectedOffset = if (index == 0) 0 else sortedFields[index - 1].offset + sortedFields[index - 1].type.size
         val paddingSize = fieldIr.offset - expectedOffset
         if (paddingSize > 0) {
            val offset = expectedOffset + headerSize
            val field = "[PADDING]"
            val type = ""
            val sbeType = ""
            val size = paddingSize.toString()
            val value = "\"\""
            addCsvRow(offset, field, type, sbeType, size, value)
         }
      }

      // show the field itself
      val offset = fieldIr.offset + headerSize
      val field = fieldIr.name
      val type = fieldIr.type.name
      val sbeType = fieldIr.type.sbeName
      val size = fieldIr.type.size.toString()
      val value = if (fieldIr.type.name == "String") field else "$field.toString()"
      addCsvRow(offset, field, type, sbeType, size, value)
   }

   // Show individual boolean fields if useFlags is enabled
   if (message.useFlags && message.booleanFields.isNotEmpty()) {
      // Calculate offset for flags field (after last non-boolean field)
      val flagsOffset = if (sortedFields.isNotEmpty()) {
         sortedFields.last().offset + sortedFields.last().type.size
      } else {
         0
      }

      // Determine size based on boolean count
      val flagsSize = when {
         message.booleanFields.size <= 8 -> 1
         message.booleanFields.size <= 16 -> 2
         message.booleanFields.size <= 32 -> 4
         else -> 8
      }

      val flagsSbeType = when (flagsSize) {
         1 -> "uint8"
         2 -> "uint16"
         4 -> "uint32"
         else -> "uint64"
      }

      // Show the packed flags field
      addCsvRow(
         flagsOffset + headerSize,
         "flags",
         "${message.name}Flags",
         flagsSbeType,
         flagsSize.toString(),
         "\"[flags]\""
      )

      // Show individual boolean fields
      message.booleanFields.forEachIndexed { index, boolField ->
         addCsvRow(
            flagsOffset + headerSize,
            "  ${boolField.name}",
            "Boolean",
            "bit[$index]",
            "1/8",
            "${boolField.name}.toString()"
         )
      }
   }

   // show final padding if there is a gap
   if (message.usePadding && sortedFields.isNotEmpty()) {
      val lastField = sortedFields.last()
      val finalPadding = message.blockLength - (lastField.offset + lastField.type.size)
      if (finalPadding > 0) {
         val offset = lastField.offset + lastField.type.size + headerSize
         val field = "[PADDING]"
         val type = ""
         val sbeType = ""
         val size = finalPadding.toString()
         val value = "\"\""
         addCsvRow(offset, field, type, sbeType, size, value)
      }
   }

   return result.joinToString("\n")
}