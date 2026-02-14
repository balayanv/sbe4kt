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
      add("sbe4kt.lib.CSV")
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
      |package $packageName
      |
      |$importStatements
      |
      |fun new${struct.name}(
      |${defaultParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |
      |fun random${struct.name}(
      |${randomParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |""".trimTemplate()
   } else {
      val structBody = generateStructBody(struct)

      """
      |package $packageName
      |
      |$importStatements
      |import sbe4kt.${struct.name}Encoder
      |import sbe4kt.${struct.name}Decoder
      |
      |fun new${struct.name}(
      |${defaultParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |
      |fun random${struct.name}(
      |${randomParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |
      |$structBody
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
   val fieldsDecOrder = struct.fields.sortedBy { it.declarationOrder }
   val defaultParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.defaultValue}," }
   val randomParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.randomValue}," }
   val args = fieldsDecOrder.joinToString(", ") { it.name }

   val structBody = generateStructBody(struct)

   return """
      |fun new${struct.name}(
      |${defaultParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |
      |fun random${struct.name}(
      |${randomParams.indent(3)}
      |): ${struct.name} {
      |   return ${struct.name}($args)
      |}
      |
      |$structBody
      |""".trimTemplate()
}

private fun generateStructBody(struct: Struct): String {
   val encoderName = struct.name.replaceFirstChar { it.lowercase() } + "Encoder"
   val decoderName = struct.name.replaceFirstChar { it.lowercase() } + "Decoder"

   val fieldsSbeOrder = struct.fields.sortedBy { it.sbeOrder }

   val readMethods = struct.fields.joinToString("\n") { "fun ${it.name}() = ${it.type.convertFromSbe("decoder.${it.name}()")}" }
   val writeMethods = struct.fields.joinToString("\n") { "fun ${it.name}(${it.name}: ${it.type.name}) = apply { encoder.${it.name}(${it.type.convertToSbe(it.name)}) }" }

   val encoderBody = fieldsSbeOrder.joinToString("\n") {
      val value = it.type.convertToSbe("source.${it.name}")
      "$encoderName.${it.name}($value)"
   }
   val decoderBody = fieldsSbeOrder.joinToString("\n") {
      val decoded = it.type.convertFromSbe("$decoderName.${it.name}()")
      "target.${it.name} = $decoded"
   }

   // Calculate actual encoded length (last field offset + size)
   val encodedLength = if (fieldsSbeOrder.isEmpty()) 0 else {
      val lastField = fieldsSbeOrder.last()
      lastField.offset + lastField.type.size
   }

   val printLayoutRows = buildString {
      for ((index, field) in fieldsSbeOrder.withIndex()) {
         // Show padding before field if there is a gap
         if (struct.usePadding && index > 0) {
            val prevField = fieldsSbeOrder[index - 1]
            val expectedOffset = prevField.offset + prevField.type.size
            val paddingSize = field.offset - expectedOffset
            if (paddingSize > 0) {
               appendLine(""".addRow("$expectedOffset", "[PADDING]", "", "", "$paddingSize", "")""")
            }
         }

         // Show the field itself
         val typeName = field.type.name
         val sbeName = field.type.sbeName
         val size = field.type.size
         val value = if (field.type.name == "String") field.name else "${field.name}.toString()"
         appendLine(""".addRow("${field.offset}", "${field.name}", "$typeName", "$sbeName", "$size", $value)""")
      }
   }.trimEnd()

   return """
      |fun encode${struct.name}(
      |   source: ${struct.name},
      |   $encoderName: ${struct.name}Encoder,
      |){
      |${encoderBody.indent(3)}
      |}
      |
      |fun decode${struct.name}(
      |   target: ${struct.name},
      |   $decoderName: ${struct.name}Decoder,
      |){
      |${decoderBody.indent(3)}
      |}
      |
      |class ${struct.name}RW(
      |   var encoder: ${struct.name}Encoder = ${struct.name}Encoder(),
      |   var decoder: ${struct.name}Decoder = ${struct.name}Decoder(),
      |) {
      |${readMethods.indent(3)}
      |${writeMethods.indent(3)}
      |
      |   fun encodeFrom(source: ${struct.name}) = apply {
      |      encode${struct.name}(source, encoder)
      |   }
      |
      |   fun decodeInto(target: ${struct.name}) = apply {
      |      decode${struct.name}(target, decoder)
      |   }
      |}
      |
      |fun ${struct.name}.printSbeLayout() {
      |   CSV()
      |      .info("${struct.name} (totalSize = $encodedLength bytes)")
      |      .addHeader("Offset", "Field", "Type", "SBE Type", "Size", "Value")
      |${printLayoutRows.indent(6)}
      |      .print()
      |}""".trimTemplate()
}
