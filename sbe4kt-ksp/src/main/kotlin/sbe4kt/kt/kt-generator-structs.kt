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
      val readMethods = struct.fields.joinToString("\n") { "fun ${it.name}() = ${it.type.convertFromSbe("decoder.${it.name}()")}" }
      val writeMethods = struct.fields.joinToString("\n") { "fun ${it.name}(${it.name}: ${it.type.name}) = apply { encoder.${it.name}(${it.type.convertToSbe(it.name)}) }" }

      """
      |@file:Suppress("unused")
      |
      |package $packageName
      |
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
      |class ${struct.name}RW(
      |   private var encoder: ${struct.name}Encoder,
      |   private var decoder: ${struct.name}Decoder,
      |) {
      |${readMethods.indent(3)}
      |${writeMethods.indent(3)}
      |}
      |
      |class ${struct.name}R(
      |   private var decoder: ${struct.name}Decoder,
      |) {
      |${readMethods.indent(3)}
      |}
      |
      |class ${struct.name}W(
      |   private var encoder: ${struct.name}Encoder,
      |) {
      |${writeMethods.indent(3)}
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
   val readMethods = struct.fields.joinToString("\n") { "fun ${it.name}() = ${it.type.convertFromSbe("decoder.${it.name}()")}" }
   val writeMethods = struct.fields.joinToString("\n") { "fun ${it.name}(${it.name}: ${it.type.name}) = apply { encoder.${it.name}(${it.type.convertToSbe(it.name)}) }" }

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
         // Show padding before field if there is a gap
         if (struct.usePadding && index > 0) {
            val prevField = sortedFields[index - 1]
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
      |class ${struct.name}RW(
      |   private var encoder: ${struct.name}Encoder,
      |   private var decoder: ${struct.name}Decoder,
      |) {
      |${readMethods.indent(3)}
      |${writeMethods.indent(3)}
      |}
      |
      |class ${struct.name}R(
      |   private var decoder: ${struct.name}Decoder,
      |) {
      |${readMethods.indent(3)}
      |}
      |
      |class ${struct.name}W(
      |   private var encoder: ${struct.name}Encoder,
      |) {
      |${writeMethods.indent(3)}
      |}
      |
      |fun ${struct.name}.printSbeLayout() {
      |   CSV()
      |      .info("${struct.name} (totalSize = $encodedLength bytes)")
      |      .addHeader("Offset", "Field", "Type", "SBE Type", "Size", "Value")
      |${printLayoutRows.indent(6)}
      |      .print()
      |}
      |""".trimTemplate()
}
