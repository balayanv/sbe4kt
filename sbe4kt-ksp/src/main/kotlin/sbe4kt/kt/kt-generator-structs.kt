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
      val codecSection = generateCompositeCodecSection(struct)
      val rwSection = generateCompositeRWSection(struct)
      val printLayoutSection = generateStructPrintLayout(struct)

      """
      |package $packageName
      |
      |$importStatements
      |import org.agrona.DirectBuffer
      |import org.agrona.MutableDirectBuffer
      |import org.agrona.concurrent.UnsafeBuffer
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
      |$codecSection
      |
      |$rwSection
      |
      |$printLayoutSection
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
      |""".trimTemplate()
}

private fun generateStructPrintLayout(struct: Struct): String {
   val fieldsSbeOrder = struct.fields.sortedBy { it.sbeOrder }
   val encodedLength = struct.encodedLength

   val printLayoutRows = buildString {
      for ((index, field) in fieldsSbeOrder.withIndex()) {
         if (struct.usePadding && index > 0) {
            val prevField = fieldsSbeOrder[index - 1]
            val expectedOffset = prevField.offset + prevField.type.size
            val paddingSize = field.offset - expectedOffset
            if (paddingSize > 0) {
               appendLine(""".addRow("$expectedOffset", "[PADDING]", "", "", "$paddingSize", "")""")
            }
         }

         val typeName = field.type.name
         val sbeName = field.type.sbeName
         val size = field.type.size
         val value = if (field.type.name == "String") field.name else "${field.name}.toString()"
         appendLine(""".addRow("${field.offset}", "${field.name}", "$typeName", "$sbeName", "$size", $value)""")
      }
   }.trimEnd()

   return """
      |fun ${struct.name}.printSbeLayout() {
      |   CSV()
      |      .info("${struct.name} (totalSize = $encodedLength bytes)")
      |      .addHeader("Offset", "Field", "Type", "SBE Type", "Size", "Value")
      |${printLayoutRows.indent(6)}
      |      .print()
      |}""".trimTemplate()
}
