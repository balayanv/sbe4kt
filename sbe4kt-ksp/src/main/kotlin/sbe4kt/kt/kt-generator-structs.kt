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
      struct.fields.forEach { field ->
         addAll(field.type.imports)
      }
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
      // Non-composites get full flyweights
      val fieldAccessors = struct.fields.joinToString("\n\n") {
         "fun ${it.name}() = ${it.type.convertFromSbe("decoder.${it.name}()")}\n" +
         "fun ${it.name}(${it.name}: ${it.type.name}) = apply { encoder.${it.name}(${it.type.convertToSbe(it.name)}) }"
      }

      val encodeBody = struct.fields.joinToString("\n") { "encoder.${it.name}(${it.type.convertToSbe("source.${it.name}")})" }
      val decodeBody = struct.fields.joinToString("\n") { "target.${it.name} = ${it.type.convertFromSbe("decoder.${it.name}()")}" }

      val sortedFields = struct.fields.sortedBy { it.sbeOrder }
      val encodedLength = if (sortedFields.isEmpty()) 0 else {
         val lastField = sortedFields.last()
         lastField.offset + lastField.type.size
      }

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

      """
      |@file:Suppress("unused")
      |
      |package $packageName
      |
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
      |class ${struct.name}RW {
      |   val encoder: ${struct.name}Encoder
      |   val decoder: ${struct.name}Decoder
      |
      |   constructor(encoder: ${struct.name}Encoder, decoder: ${struct.name}Decoder){
      |      this.encoder = encoder
      |      this.decoder = decoder
      |   }
      |
      |   constructor(buffer: MutableDirectBuffer, offset: Int){
      |      this.encoder = ${struct.name}Encoder()
      |      this.decoder = ${struct.name}Decoder()
      |   }
      |
      |   fun wrap(buffer: MutableDirectBuffer, offset: Int) = apply {
      |      encoder.wrap(buffer, offset)
      |      decoder.wrap(buffer, offset)
      |   }
      |
      |${fieldAccessors.indent(3)}
      |
      |   fun encodeFrom(source: ${struct.name}): Int {
      |${encodeBody.indent(6)}
      |      return encoder.encodedLength()
      |   }
      |
      |   fun decodeInto(target: ${struct.name} = new${struct.name}()): Int {
      |${decodeBody.indent(6)}
      |      return decoder.encodedLength()
      |   }
      |}
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

internal fun KSP.generateComposite(struct: Composite): String {
   val fieldAccessors = struct.fields.joinToString("\n\n") {
      "fun ${it.name}() = ${it.type.convertFromSbe("decoder.${it.name}()")}\n" +
      "fun ${it.name}(${it.name}: ${it.type.name}) = apply { encoder.${it.name}(${it.type.convertToSbe(it.name)}) }"
   }

   val encodeBody = struct.fields.joinToString("\n") { "encoder.${it.name}(${it.type.convertToSbe("source.${it.name}")})" }
   val decodeBody = struct.fields.joinToString("\n") { "target.${it.name} = ${it.type.convertFromSbe("decoder.${it.name}()")}" }

   val fieldsDecOrder = struct.fields.sortedBy { it.declarationOrder }
   val defaultParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.defaultValue}," }
   val randomParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.randomValue}," }
   val args = fieldsDecOrder.joinToString(", ") { it.name }

   val sortedFields = struct.fields.sortedBy { it.sbeOrder }

   // Calculate actual encoded length (last field offset + size)
   val encodedLength = if (sortedFields.isEmpty()) 0 else {
      val lastField = sortedFields.last()
      lastField.offset + lastField.type.size
   }

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
      |class ${struct.name}RW {
      |   val encoder: ${struct.name}Encoder
      |   val decoder: ${struct.name}Decoder
      |
      |   constructor(encoder: ${struct.name}Encoder, decoder: ${struct.name}Decoder){
      |      this.encoder = encoder
      |      this.decoder = decoder
      |   }
      |
      |   constructor(buffer: MutableDirectBuffer, offset: Int){
      |      this.encoder = ${struct.name}Encoder()
      |      this.decoder = ${struct.name}Decoder()
      |   }
      |
      |   fun wrap(buffer: MutableDirectBuffer, offset: Int) = apply {
      |      encoder.wrap(buffer, offset)
      |      decoder.wrap(buffer, offset)
      |   }
      |
      |${fieldAccessors.indent(3)}
      |
      |   fun encodeFrom(source: ${struct.name}): Int {
      |${encodeBody.indent(6)}
      |      return encoder.encodedLength()
      |   }
      |
      |   fun decodeInto(target: ${struct.name} = new${struct.name}()): Int {
      |${decodeBody.indent(6)}
      |      return decoder.encodedLength()
      |   }
      |}
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
