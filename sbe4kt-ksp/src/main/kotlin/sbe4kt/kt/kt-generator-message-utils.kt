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
      |fun $$className.sbeLayout() : CSV {
      |   return CSV()
      |      .info("$$className (totalSize = $$$sizeConstName bytes)")
      |      .addHeader("Offset", "Field", "Type", "SBE Type", "Size", "Value")
      |      .separator()
      |      .addRow("0", "[ROOT]", "$$className", "message", "$$$sizeConstName", this.toString(), metadata = "optional.info")
      |      .separator(metadata = "optional.separator")
      |      .addRow("0", "[HEADER]", "Header", "", "${MessageHeaderEncoder.ENCODED_LENGTH}", "${$${className}Encoder.BLOCK_LENGTH}|${$${className}Encoder.TEMPLATE_ID}|${$${className}Encoder.SCHEMA_ID}|${$${className}Encoder.SCHEMA_VERSION}")
      |$${rows.indent(6)}
      |}
      |
      |fun $$className.printSbeLayout(verbose: Boolean = false) {
      |   val csv = this.sbeLayout()
      |   if (verbose) {
      |      csv.print()
      |   } else {
      |      val optionalRowsRemoved = csv.rows
      |         .filterNot { it.metadata.startsWith("optional") }
      |         .toMutableList()
      |      CSV(optionalRowsRemoved).print()
      |   }
      |}
      |""".trimTemplate()
}

private fun generateRows(message: Message): String {
   val result = mutableListOf<String>()

   fun addCsvRow(offset: Int, field: String, type: String, sbeType: String, size: String, value: String, metadata: String = "") {
      if (metadata.isNotEmpty()) {
         result.add(""".addRow("$offset", "$field", "$type", "$sbeType", "$size", $value, metadata = "$metadata")""")
      } else {
         result.add(""".addRow("$offset", "$field", "$type", "$sbeType", "$size", $value)""")
      }
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
            addCsvRow(offset, "[PADDING]", "", "", paddingSize.toString(), "\"\"")
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
         addCsvRow(offset, "[PADDING]", "", "", finalPadding.toString(), "\"\"")
      }
   }

   return result.joinToString("\n")
}
