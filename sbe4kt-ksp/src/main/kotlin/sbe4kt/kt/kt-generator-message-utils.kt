package sbe4kt.kt

import sbe4kt.ir.ByteArrayN
import sbe4kt.ir.DoubleArrayN
import sbe4kt.ir.FloatArrayN
import sbe4kt.ir.IntArrayN
import sbe4kt.ir.CharArrayN
import sbe4kt.ir.LongArrayN
import sbe4kt.ir.ShortArrayN
import sbe4kt.ir.Message
import sbe4kt.ir.Struct
import sbe4kt.ir.ArrayN
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate

internal fun generateUtilsSection(message: Message): String {
   val className = message.name
   val codecName = "${className}Codec"
   val rows = generateRows(message)
   return $$"""
      |fun $$className.sbeLayout() : CSV {
      |   return CSV()
      |      .info("$$className (totalSize = ${$$codecName.SIZE} bytes)")
      |      .addHeader("Offset", "Field", "Type", "SBE Type", "Size", "Value")
      |      .separator()
      |      .addRow("0", "[ROOT]", "$$className", "message", "${$$codecName.SIZE}", this.toString(), metadata = "optional.info")
      |      .separator(metadata = "optional.separator")
      |      .addRow("0", "[HEADER]", "Header", "", "${$$codecName.HEADER_SIZE}", "${$$codecName.BLOCK_LENGTH}|${$$codecName.TEMPLATE_ID}|${$$codecName.SCHEMA_ID}|${$$codecName.SCHEMA_VERSION}")
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

   fun addSeparator(metadata: String = "") {
      if (metadata.isNotEmpty()) {
         result.add(""".separator(metadata = "$metadata")""")
      } else {
         result.add(".separator()")
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

      if (fieldIr.type is Struct) {
         val struct = fieldIr.type as Struct
         val offset = fieldIr.offset + headerSize

         addSeparator(metadata = "optional.separator")
         addCsvRow(offset, fieldIr.name, struct.name, "struct", struct.size.toString(), "${fieldIr.name}.toString()", metadata = "optional.info")
         addSeparator(metadata = "optional.separator")

         val subFields = struct.fields.sortedBy { it.sbeOrder }
         for (subField in subFields) {
            val subOffset = fieldIr.offset + subField.offset + headerSize
            val subType = subField.type.name
            val subSbeType = subField.type.sbeName
            val subSize = subField.type.size.toString()
            val subValue = if (subField.type.name == "String") "${fieldIr.name}.${subField.name}" else "${fieldIr.name}.${subField.name}.toString()"
            addCsvRow(subOffset, subField.name, subType, subSbeType, subSize, subValue)
         }
      } else {
         val offset = fieldIr.offset + headerSize
         val field = fieldIr.name
         val type = if (fieldIr.type is ArrayN) "${(fieldIr.type as ArrayN).structType.name}[${(fieldIr.type as ArrayN).length}]" else fieldIr.type.name
         val sbeType = fieldIr.type.sbeName
         val size = fieldIr.type.size.toString()
         val value = when {
            fieldIr.type is ArrayN -> "$field.contentToString()"
            fieldIr.type is ByteArrayN || fieldIr.type is ShortArrayN || fieldIr.type is IntArrayN || fieldIr.type is LongArrayN || fieldIr.type is FloatArrayN || fieldIr.type is DoubleArrayN || fieldIr.type is CharArrayN -> "$field.contentToString()"
            else -> "$field.toString()"
         }
         addCsvRow(offset, field, type, sbeType, size, value)
      }
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
