package sbe4kt.kt

import com.google.devtools.ksp.processing.Dependencies
import sbe4kt.KSP
import sbe4kt.ir.Composite
import sbe4kt.ir.Struct
import sbe4kt.util.indent
import sbe4kt.util.isStruct
import sbe4kt.util.trimTemplate

internal fun KSP.generateStructFile(struct: Struct, needsFlyweights: Boolean = true) {
   val packageName = struct.declaration.packageName.asString()
   val className = "${struct.name}Extensions"

   val imports = buildSet {
      add("sbe4kt.lib.SbeLayoutRow")
      add("sbe4kt.lib.toCSV")
      add("kotlin.random.Random")
      struct.fields.forEach { addAll(it.type.imports) }
   }
   val importStatements = imports.joinToString("\n") { "import $it" }

   val fieldsDecOrder = struct.fields.sortedBy { it.declarationOrder }
   val defaultParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.defaultValue}," }
   val randomParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.randomValue}," }
   val args = fieldsDecOrder.joinToString(", ") { it.name }

   val content = if (!needsFlyweights) {
      // Structs without flyweights only get helper functions
      // (e.g., composites used only in unions don't have encoder/decoder classes)
      """
      |@file:Suppress("unused")
      |
      |package $packageName
      |
      |$importStatements
      |
      |@JvmOverloads
      |fun new${struct.name}(
      |${defaultParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |
      |@JvmOverloads
      |fun random${struct.name}(
      |${randomParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |""".trimTemplate()
   } else {
      val sortedFields = struct.fields.sortedBy { it.sbeOrder }

      val layoutRows = buildString {
         for ((index, field) in sortedFields.withIndex()) {
            if (struct.usePadding && index > 0) {
               val prevField = sortedFields[index - 1]
               val expectedOffset = prevField.offset + prevField.type.size
               val paddingSize = field.offset - expectedOffset
               if (paddingSize > 0) {
                  appendLine("""SbeLayoutRow(startOffset + $expectedOffset, "[PADDING]", "", "", $paddingSize, ""),""")
               }
            }
            val typeName = field.type.name
            val sbeName = field.type.sbeName
            val size = field.type.size
            val value = if (field.type.name == "String") field.name else "${field.name}.toString()"
            appendLine("""SbeLayoutRow(startOffset + ${field.offset}, "${field.name}", "$typeName", "$sbeName", $size, $value),""")
         }
      }.trimEnd()

      val readerClass = generateStructReader(struct)
      val writerClass = generateStructWriter(struct)

      """
      |@file:Suppress("unused")
      |
      |package $packageName
      |
      |import org.agrona.DirectBuffer
      |import org.agrona.MutableDirectBuffer
      |$importStatements
      |import sbe4kt.${struct.name}Encoder
      |import sbe4kt.${struct.name}Decoder
      |
      |@JvmOverloads
      |fun new${struct.name}(
      |${defaultParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |
      |@JvmOverloads
      |fun random${struct.name}(
      |${randomParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |
      |${readerClass}
      |
      |${writerClass}
      |
      |internal fun ${struct.name}.sbeLayout(startOffset: Int = 0) : List<SbeLayoutRow> {
      |   return listOf(
      |${layoutRows.indent(6)}
      |   )
      |}
      |
      |fun ${struct.name}.printSbeLayout() {
      |   sbeLayout().toCSV().print()
      |}
      |""".trimTemplate()
   }

   val file = gen.createNewFile(
      Dependencies(true, *struct.declaration.containingFile?.let { arrayOf(it) } ?: emptyArray()),
      packageName,
      className
   )

   file.bufferedWriter().use { it.write(content) }
}

private fun generateStructReader(struct: Struct): String {
   val fieldGetters = struct.fields.joinToString("\n\n") {
      "fun ${it.name}() = ${it.type.convertFromSbe("decoder.${it.name}()")}"
   }
   val decodeBody = struct.fields.joinToString("\n") { "target.${it.name} = ${it.type.convertFromSbe("decoder.${it.name}()")}" }

   return """
      |class ${struct.name}R {
      |   val decoder: ${struct.name}Decoder
      |
      |   constructor(decoder: ${struct.name}Decoder) {
      |      this.decoder = decoder
      |   }
      |
      |   constructor(buffer: DirectBuffer, offset: Int) {
      |      this.decoder = ${struct.name}Decoder()
      |      wrap(buffer, offset)
      |   }
      |
      |   fun wrap(buffer: DirectBuffer, offset: Int) = apply {
      |      decoder.wrap(buffer, offset)
      |   }
      |
      |${fieldGetters.indent(3)}
      |
      |   fun decodeInto(target: ${struct.name} = new${struct.name}()): Int {
      |${decodeBody.indent(6)}
      |      return decoder.encodedLength()
      |   }
      |}""".trimTemplate()
}

private fun generateStructWriter(struct: Struct): String {
   val fieldSetters = struct.fields.joinToString("\n\n") {
      "fun ${it.name}(${it.name}: ${it.type.name}) = apply { encoder.${it.name}(${it.type.convertToSbe(it.name)}) }"
   }
   val encodeBody = struct.fields.joinToString("\n") { "encoder.${it.name}(${it.type.convertToSbe("source.${it.name}")})" }

   return """
      |class ${struct.name}W {
      |   val encoder: ${struct.name}Encoder
      |
      |   constructor(encoder: ${struct.name}Encoder) {
      |      this.encoder = encoder
      |   }
      |
      |   constructor(buffer: MutableDirectBuffer, offset: Int) {
      |      this.encoder = ${struct.name}Encoder()
      |      wrap(buffer, offset)
      |   }
      |
      |   fun wrap(buffer: MutableDirectBuffer, offset: Int) = apply {
      |      encoder.wrap(buffer, offset)
      |   }
      |
      |${fieldSetters.indent(3)}
      |
      |   fun encodeFrom(source: ${struct.name}): Int {
      |${encodeBody.indent(6)}
      |      return encoder.encodedLength()
      |   }
      |}""".trimTemplate()
}

internal fun KSP.generateComposite(struct: Composite): String {
   val fieldsDecOrder = struct.fields.sortedBy { it.declarationOrder }
   val defaultParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.defaultValue}," }
   val randomParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.randomValue}," }
   val args = fieldsDecOrder.joinToString(", ") { it.name }

   val sortedFields = struct.fields.sortedBy { it.sbeOrder }

   val printLayoutRows = buildString {
      for ((index, field) in sortedFields.withIndex()) {
         if (struct.usePadding && index > 0) {
            val prevField = sortedFields[index - 1]
            val expectedOffset = prevField.offset + prevField.type.size
            val paddingSize = field.offset - expectedOffset
            if (paddingSize > 0) {
               appendLine("""SbeLayoutRow(startOffset + $expectedOffset, "[PADDING]", "", "", $paddingSize, ""),""")
            }
         }
         val typeName = field.type.name
         val sbeName = field.type.sbeName
         val size = field.type.size
         val value = if (field.type.name == "String") field.name else "${field.name}.toString()"
         appendLine("""SbeLayoutRow(startOffset + ${field.offset}, "${field.name}", "$typeName", "$sbeName", $size, $value),""")
      }
   }.trimEnd()

   val readerClass = generateStructReader(struct)
   val writerClass = generateStructWriter(struct)

   return """
      |@JvmOverloads
      |fun new${struct.name}(
      |${defaultParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |
      |@JvmOverloads
      |fun random${struct.name}(
      |${randomParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |
      |${readerClass}
      |
      |${writerClass}
      |
      |internal fun ${struct.name}.sbeLayout(startOffset: Int = 0) : List<SbeLayoutRow> {
      |   return listOf(
      |${printLayoutRows.indent(6)}
      |   )
      |}
      |
      |fun ${struct.name}.printSbeLayout() {
      |   sbeLayout().toCSV().print()
      |}
      |""".trimTemplate()
}
