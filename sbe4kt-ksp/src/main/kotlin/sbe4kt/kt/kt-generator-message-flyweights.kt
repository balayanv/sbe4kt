package sbe4kt.kt

import sbe4kt.ir.Message
import sbe4kt.ir.StringN
import sbe4kt.ir.Struct
import sbe4kt.ir.ArrayN
import sbe4kt.ir.Tags
import sbe4kt.ir.Union
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate

internal fun generateFlyweightsSection(message: Message): String {
   val className = message.name
   val codecName = "${className}Codec"

   val hasUnsupportedFields = message.fields.any { !isSupportedCodecType(it.type) }
   if (hasUnsupportedFields) {
      return "// ${className}FW skipped: contains unsupported field types (not yet supported)"
   }

   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }

   // nested FW fields initialized with shared buffer and computed offset
   val nestedFwFields = mutableListOf<String>()
   val nestedFwVarNames = mutableListOf<Pair<String, String>>() // varName to typeName

   for (field in fieldsDecOrder) {
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      when (field.type) {
         is Struct, is Message -> {
            nestedFwFields.add("private val ${field.name}FW = ${field.type.name}FW(buffer, $codecName.offset${fieldCap}(offset))")
            nestedFwVarNames.add("${field.name}FW" to "${field.type.name}FW")
         }
         is ArrayN -> {
            if (field.type.isStruct) {
               val structName = field.type.structType.name
               val elementVar = "${field.name}ElementFW"
               nestedFwFields.add("private val $elementVar = ${structName}FW(buffer)")
               nestedFwFields.add("private val ${field.name}FW = ArrayFW<${structName}FW>(${field.type.length}, ${structName}Codec.SIZE, { _, off, _ -> $elementVar.also { it.offset(off) } }, buffer, $codecName.offset${fieldCap}(offset))")
            } else {
               nestedFwFields.add("private val ${field.name}FW = ${field.type.fwName}(buffer, ${field.type.length}, $codecName.offset${fieldCap}(offset))")
            }
            nestedFwVarNames.add("${field.name}FW" to field.type.fwName)
         }
         is StringN -> {
            nestedFwFields.add("private val ${field.name}FW = CharArrayFW(buffer, ${field.type.length}, $codecName.offset${fieldCap}(offset))")
            nestedFwVarNames.add("${field.name}FW" to "CharArrayFW")
         }
         is Union -> {
            val union = field.type
            for (variant in union.allowedTypes) {
               val variantLower = variant.name.replaceFirstChar { it.lowercase() }
               nestedFwFields.add("private val ${variantLower}FW = ${variant.name}FW(buffer, $codecName.offset${fieldCap}(offset))")
               nestedFwVarNames.add("${variantLower}FW" to "${variant.name}FW")
            }
         }
         else -> {}
      }
   }

   // field accessors
   val fwAccessors = fieldsDecOrder.joinToString("\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      when (field.type) {
         is Struct, is Message -> {
            "fun ${field.name}() = ${field.name}FW"
         }
         is Union -> {
            val union = field.type
            val lines = mutableListOf<String>()
            lines.add("fun ${field.name}Id(): Int = $codecName.readTemplateId(buffer, $codecName.offset${fieldCap}(offset))")
            for (variant in union.allowedTypes) {
               val variantLower = variant.name.replaceFirstChar { it.lowercase() }
               val variantCap = variant.name.replaceFirstChar { it.uppercase() }
               lines.add("fun ${field.name}${variantCap}() = ${variantLower}FW")
            }
            val whenBranches = union.allowedTypes.joinToString("\n") { variant ->
               "   is ${variant.name} -> ${variant.name}Codec.writeFrom(value, buffer, $codecName.offset${fieldCap}(offset))"
            }
            val elseClause = if (union.typeName == "Any") {
               $$"\n   else -> throw IllegalArgumentException(\"Unexpected type in union field '$${field.name}': ${value::class.qualifiedName}\")"
            } else ""
            lines.add("fun write${fieldCap}(value: ${union.typeName}) = apply {\n   when (value) {\n$whenBranches$elseClause\n   }\n}")
            val getBranches = union.allowedTypes.joinToString("\n") { variant ->
               val variantLower = variant.name.replaceFirstChar { it.lowercase() }
               "   ${variant.name}Codec.TEMPLATE_ID -> ${variantLower}FW"
            }
            lines.add("fun ${field.name}() = when (${field.name}Id()) {\n$getBranches\n   else -> throw IllegalStateException(\"Unknown templateId in union field '${field.name}'\")\n}")
            lines.joinToString("\n")
         }
         is ArrayN, is StringN -> {
            "fun ${field.name}() = ${field.name}FW"
         }
         is Tags -> {
            field.type.generateFlyweightAccessors(field.name, codecName)
         }
         else -> {
            val rawExpr = "$codecName.read${fieldCap}(buffer, offset)"
            val converted = convertFromSbe(field.type, rawExpr)
            val convertedSet = convertToSbe(field.type, field.name)
            "fun write${fieldCap}(${field.name}: ${field.type.name}) = apply { $codecName.write${fieldCap}(buffer, offset, $convertedSet) }\n" +
            "fun read${fieldCap}() = $converted"
         }
      }
   }

   val nestedFwSection = if (nestedFwFields.isEmpty()) "" else nestedFwFields.joinToString("\n").indent(3) + "\n"

   val toStringMethod = generateFwToString("${className}FW", nestedFwVarNames)

   return """
      |class ${className}FW(
      |   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray($codecName.SIZE)),
      |   val offset: Int = 0,
      |) {
      |${nestedFwSection}
      |   fun writeHeader() = apply { $codecName.writeHeader(buffer, offset) }
      |   fun readHeader() = $codecName.readHeader(buffer, offset)
      |   fun readBlockLength() = $codecName.readBlockLength(buffer, offset)
      |   fun readTemplateId() = $codecName.readTemplateId(buffer, offset)
      |   fun readSchemaId() = $codecName.readSchemaId(buffer, offset)
      |   fun readVersion() = $codecName.readVersion(buffer, offset)
      |
      |${fwAccessors.indent(3)}
      |
      |   fun encode(from: $className) = apply {
      |      $codecName.writeFrom(from, buffer, offset)
      |   }
      |
      |   fun decode(target: $className = new$className()) = apply {
      |      $codecName.readInto(target, buffer, offset)
      |   }
      |
      |   fun size() = $codecName.SIZE
      |
      |${toStringMethod.indent(3)}
      |}
      |""".trimTemplate()
}

// --- composite FW (no header, flat fields) ---

internal fun generateCompositeFWSection(struct: Struct): String {
   val className = struct.name
   val codecName = "${className}Codec"

   val hasUnsupportedFields = struct.fields.any { !isSupportedCodecType(it.type) }
   if (hasUnsupportedFields) {
      return "// ${className}FW skipped: contains unsupported field types"
   }

   val fieldsDecOrder = struct.fields.sortedBy { it.declarationOrder }

   val fwAccessors = fieldsDecOrder.joinToString("\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      val rawExpr = "$codecName.read${fieldCap}(buffer, offset)"
      val converted = convertFromSbe(field.type, rawExpr)
      val convertedSet = convertToSbe(field.type, field.name)
      "fun write${fieldCap}(${field.name}: ${field.type.name}) = apply { $codecName.write${fieldCap}(buffer, offset, $convertedSet) }\n" +
      "fun read${fieldCap}() = $converted"
   }

   val toStringMethod = generateFwToString("${className}FW", emptyList())

   return """
      |class ${className}FW(
      |   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray($codecName.SIZE)),
      |   val offset: Int = 0,
      |) {
      |${fwAccessors.indent(3)}
      |
      |   fun encode(from: $className) = apply {
      |      $codecName.writeFrom(from, buffer, offset)
      |   }
      |
      |   fun decode(target: $className = new$className()) = apply {
      |      $codecName.readInto(target, buffer, offset)
      |   }
      |
      |   fun size() = $codecName.SIZE
      |
      |${toStringMethod.indent(3)}
      |}
      |""".trimTemplate()
}

private fun generateFwToString(fwClassName: String, nestedVarNames: List<Pair<String, String>>): String {
   val sb = StringBuilder()
   sb.appendLine("override fun toString(): String = \"\"\"")
   sb.appendLine("   $fwClassName {")
   sb.appendLine($$"      buffer -> UnsafeBuffer@${System.identityHashCode(buffer)}[${buffer.capacity()}]")
   sb.appendLine($$"      offset: $offset")
   for ((varName, typeName) in nestedVarNames) {
      sb.appendLine("      $typeName {")
      sb.appendLine($$"         buffer -> UnsafeBuffer@${System.identityHashCode($$varName.buffer)}[${$$varName.buffer.capacity()}]")
      sb.appendLine("      }")
   }
   sb.appendLine("   }")
   sb.append("\"\"\".trimIndent()")
   return sb.toString()
}
