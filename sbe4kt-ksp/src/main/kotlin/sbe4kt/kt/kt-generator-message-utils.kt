package sbe4kt.kt

import sbe4kt.ir.Message
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate

internal fun generateUtilsSection(message: Message): String {
   val className = message.name
   val codecName = "${className}Codec"
   val rows = generateRows(message)
   return $$"""
      |fun $$className.printSbeLayout() {
      |   CSV()
      |      .info("$$className (totalSize = ${$$codecName.SIZE} bytes)")
      |      .addHeader("Offset", "Field", "Type", "SBE Type", "Size", "Value")
      |      .addRow("0", "[HEADER]", "Header", "", "${$$codecName.HEADER_SIZE}", "${$$codecName.BLOCK_LENGTH}|${$$codecName.TEMPLATE_ID}|${$$codecName.SCHEMA_ID}|${$$codecName.SCHEMA_VERSION}")
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
