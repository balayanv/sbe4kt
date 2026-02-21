package sbe4kt.kt

import sbe4kt.ir.Message
import sbe4kt.ir.Struct
import sbe4kt.ir.Union
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate

internal fun generateFlyweightsSection(message: Message): String {
   val className = message.name
   val codecName = "${className}Codec"

   val hasUnsupportedFields = message.fields.any { !isSupportedCodecType(it.type) }
   if (hasUnsupportedFields) {
      return "// ${className}RW skipped: contains unsupported field types (not yet supported)"
   }

   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }

   // pre-allocated RWs for composite, nested message, and union variant fields
   val nestedRwFields = mutableListOf<String>()
   val nestedWraps = mutableListOf<String>()

   for (field in fieldsDecOrder) {
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      when (field.type) {
         is Struct, is sbe4kt.ir.Message -> {
            val rwVar = "${field.name}RW"
            nestedRwFields.add("private val $rwVar = ${field.type.name}RW(buffer, $codecName.offset${fieldCap}(offset))")
            nestedWraps.add("$rwVar.wrap(buffer, $codecName.offset${fieldCap}(offset))")
         }
         is Union -> {
            val union = field.type as Union
            for (variant in union.allowedTypes) {
               val variantLower = variant.name.replaceFirstChar { it.lowercase() }
               nestedRwFields.add("private val ${variantLower}RW = ${variant.name}RW(buffer, $codecName.offset${fieldCap}(offset))")
               nestedWraps.add("${variantLower}RW.wrap(buffer, $codecName.offset${fieldCap}(offset))")
            }
         }
         else -> {}
      }
   }

   // field accessors
   val fwAccessors = fieldsDecOrder.joinToString("\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      when (field.type) {
         is Struct, is sbe4kt.ir.Message -> {
            "fun ${field.name}() = ${field.name}RW"
         }
         is Union -> {
            val union = field.type as Union
            val lines = mutableListOf<String>()
            lines.add("fun ${field.name}Id(): Int = $codecName.templateId(buffer, $codecName.offset${fieldCap}(offset))")
            for (variant in union.allowedTypes) {
               val variantLower = variant.name.replaceFirstChar { it.lowercase() }
               val variantCap = variant.name.replaceFirstChar { it.uppercase() }
               lines.add("fun ${field.name}${variantCap}() = ${variantLower}RW")
            }
            // union setter: encodes the right variant based on type
            val whenBranches = union.allowedTypes.joinToString("\n") { variant ->
               "   is ${variant.name} -> ${variant.name}Codec.encodeFrom(value, buffer, $codecName.offset${fieldCap}(offset))"
            }
            val elseClause = if (union.typeName == "Any") {
               "\n   else -> throw IllegalArgumentException(\"Unexpected type in union field '${field.name}': \${value::class.qualifiedName}\")"
            } else ""
            lines.add("fun ${field.name}(value: ${union.typeName}) = apply {\n   when (value) {\n$whenBranches$elseClause\n   }\n}")
            lines.joinToString("\n")
         }
         else -> {
            val rawExpr = "$codecName.${field.name}(buffer, offset)"
            val converted = convertFromSbe(field.type, rawExpr)
            val convertedSet = convertToSbe(field.type, field.name)
            "fun ${field.name}() = $converted\n" +
            "fun ${field.name}(${field.name}: ${field.type.name}) = apply { $codecName.${field.name}(buffer, offset, $convertedSet) }"
         }
      }
   }

   val nestedRwSection = if (nestedRwFields.isEmpty()) "" else nestedRwFields.joinToString("\n").indent(3) + "\n"
   val wrapBody = if (nestedWraps.isEmpty()) "" else "\n" + nestedWraps.joinToString("\n")

   return """
      |class ${className}RW(
      |   private var buffer: MutableDirectBuffer = UnsafeBuffer(ByteArray($codecName.SIZE)),
      |   private var offset: Int = 0,
      |) {
      |${nestedRwSection}
      |   fun header() = apply { $codecName.header(buffer, offset) }
      |   fun blockLength() = $codecName.blockLength(buffer, offset)
      |   fun templateId() = $codecName.templateId(buffer, offset)
      |   fun schemaId() = $codecName.schemaId(buffer, offset)
      |   fun version() = $codecName.version(buffer, offset)
      |
      |   fun wrap(buffer: MutableDirectBuffer, offset: Int) = apply {
      |      this.buffer = buffer
      |      this.offset = offset${wrapBody.indent(6)}
      |   }
      |
      |${fwAccessors.indent(3)}
      |
      |   fun encodeFrom(source: $className) = apply {
      |      $codecName.encodeFrom(source, buffer, offset)
      |   }
      |
      |   fun decodeInto(target: $className) = apply {
      |      $codecName.decodeInto(target, buffer, offset)
      |   }
      |}
      |""".trimTemplate()
}

// --- composite RW (no header, flat fields) ---

internal fun generateCompositeRWSection(struct: Struct): String {
   val className = struct.name
   val codecName = "${className}Codec"
   val encodedLength = struct.encodedLength

   val hasUnsupportedFields = struct.fields.any { !isSupportedCodecType(it.type) }
   if (hasUnsupportedFields) {
      return "// ${className}RW skipped: contains unsupported field types"
   }

   val fieldsDecOrder = struct.fields.sortedBy { it.declarationOrder }

   val fwAccessors = fieldsDecOrder.joinToString("\n") { field ->
      val rawExpr = "$codecName.${field.name}(buffer, offset)"
      val converted = convertFromSbe(field.type, rawExpr)
      val convertedSet = convertToSbe(field.type, field.name)
      "fun ${field.name}() = $converted\n" +
      "fun ${field.name}(${field.name}: ${field.type.name}) = apply { $codecName.${field.name}(buffer, offset, $convertedSet) }"
   }

   return """
      |class ${className}RW(
      |   private var buffer: MutableDirectBuffer = UnsafeBuffer(ByteArray($encodedLength)),
      |   private var offset: Int = 0,
      |) {
      |
      |   fun wrap(buffer: MutableDirectBuffer, offset: Int) = apply {
      |      this.buffer = buffer
      |      this.offset = offset
      |   }
      |
      |${fwAccessors.indent(3)}
      |
      |   fun encodeFrom(source: $className) = apply {
      |      $codecName.encodeFrom(source, buffer, offset)
      |   }
      |
      |   fun decodeInto(target: $className) = apply {
      |      $codecName.decodeInto(target, buffer, offset)
      |   }
      |}
      |""".trimTemplate()
}
