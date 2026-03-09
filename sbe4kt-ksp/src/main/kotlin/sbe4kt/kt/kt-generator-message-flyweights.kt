package sbe4kt.kt

import sbe4kt.ir.ByteArrayN
import sbe4kt.ir.DoubleArrayN
import sbe4kt.ir.FloatArrayN
import sbe4kt.ir.IntArrayN
import sbe4kt.ir.CharArrayN
import sbe4kt.ir.LongArrayN
import sbe4kt.ir.ShortArrayN
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

   for (field in fieldsDecOrder) {
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      when (field.type) {
         is Struct, is sbe4kt.ir.Message -> {
            nestedFwFields.add("private val ${field.name}FW = ${field.type.name}FW(buffer, $codecName.offset${fieldCap}(offset))")
         }
         is ByteArrayN -> {
            nestedFwFields.add("private val ${field.name}FW = ByteArrayFW(${field.type.length}, buffer, $codecName.offset${fieldCap}(offset))")
         }
         is ShortArrayN -> {
            nestedFwFields.add("private val ${field.name}FW = ShortArrayFW(${field.type.length}, buffer, $codecName.offset${fieldCap}(offset))")
         }
         is IntArrayN -> {
            nestedFwFields.add("private val ${field.name}FW = IntArrayFW(${field.type.length}, buffer, $codecName.offset${fieldCap}(offset))")
         }
         is LongArrayN -> {
            nestedFwFields.add("private val ${field.name}FW = LongArrayFW(${field.type.length}, buffer, $codecName.offset${fieldCap}(offset))")
         }
         is FloatArrayN -> {
            nestedFwFields.add("private val ${field.name}FW = FloatArrayFW(${field.type.length}, buffer, $codecName.offset${fieldCap}(offset))")
         }
         is DoubleArrayN -> {
            nestedFwFields.add("private val ${field.name}FW = DoubleArrayFW(${field.type.length}, buffer, $codecName.offset${fieldCap}(offset))")
         }
         is CharArrayN -> {
            nestedFwFields.add("private val ${field.name}FW = CharArrayFW(${(field.type as CharArrayN).length}, buffer, $codecName.offset${fieldCap}(offset))")
         }
         is StringN -> {
            nestedFwFields.add("private val ${field.name}FW = CharArrayFW(${(field.type as StringN).length}, buffer, $codecName.offset${fieldCap}(offset))")
         }
         is ArrayN -> {
            val structArr = field.type as ArrayN
            val structName = structArr.structType.name
            val elementVar = "${field.name}ElementFW"
            nestedFwFields.add("private val $elementVar = ${structName}FW(buffer, 0)")
            nestedFwFields.add("private val ${field.name}FW = ArrayFW<${structName}FW>(${structArr.length}, ${structName}Codec.SIZE, { _, off, _ -> $elementVar.also { it.offset = off } }, buffer, $codecName.offset${fieldCap}(offset))")
         }
         is Union -> {
            val union = field.type as Union
            for (variant in union.allowedTypes) {
               val variantLower = variant.name.replaceFirstChar { it.lowercase() }
               nestedFwFields.add("private val ${variantLower}FW = ${variant.name}FW(buffer, $codecName.offset${fieldCap}(offset))")
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
            "fun ${field.name}() = ${field.name}FW"
         }
         is Union -> {
            val union = field.type as Union
            val lines = mutableListOf<String>()
            lines.add("fun ${field.name}Id(): Int = $codecName.templateId(buffer, $codecName.offset${fieldCap}(offset))")
            for (variant in union.allowedTypes) {
               val variantLower = variant.name.replaceFirstChar { it.lowercase() }
               val variantCap = variant.name.replaceFirstChar { it.uppercase() }
               lines.add("fun ${field.name}${variantCap}() = ${variantLower}FW")
            }
            val whenBranches = union.allowedTypes.joinToString("\n") { variant ->
               "   is ${variant.name} -> ${variant.name}Codec.encodeFrom(value, buffer, $codecName.offset${fieldCap}(offset))"
            }
            val elseClause = if (union.typeName == "Any") {
               "\n   else -> throw IllegalArgumentException(\"Unexpected type in union field '${field.name}': \${value::class.qualifiedName}\")"
            } else ""
            lines.add("fun ${field.name}(value: ${union.typeName}) = apply {\n   when (value) {\n$whenBranches$elseClause\n   }\n}")
            val getBranches = union.allowedTypes.joinToString("\n") { variant ->
               val variantLower = variant.name.replaceFirstChar { it.lowercase() }
               "   ${variant.name}Codec.TEMPLATE_ID -> ${variantLower}FW"
            }
            lines.add("fun ${field.name}() = when (${field.name}Id()) {\n$getBranches\n   else -> throw IllegalStateException(\"Unknown templateId in union field '${field.name}'\")\n}")
            lines.joinToString("\n")
         }
         is ArrayN -> {
            "fun ${field.name}() = ${field.name}FW"
         }
         is ByteArrayN -> {
            "fun ${field.name}() = ${field.name}FW"
         }
         is ShortArrayN -> {
            "fun ${field.name}() = ${field.name}FW"
         }
         is IntArrayN -> {
            "fun ${field.name}() = ${field.name}FW"
         }
         is LongArrayN -> {
            "fun ${field.name}() = ${field.name}FW"
         }
         is FloatArrayN -> {
            "fun ${field.name}() = ${field.name}FW"
         }
         is DoubleArrayN -> {
            "fun ${field.name}() = ${field.name}FW"
         }
         is CharArrayN -> {
            "fun ${field.name}() = ${field.name}FW"
         }
         is StringN -> {
            "fun ${field.name}() = ${field.name}FW"
         }
         is Tags -> {
            field.type.generateFlyweightAccessors(field.name, codecName)
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

   val nestedFwSection = if (nestedFwFields.isEmpty()) "" else nestedFwFields.joinToString("\n").indent(3) + "\n"

   return """
      |class ${className}FW(
      |   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray($codecName.SIZE)),
      |   var offset: Int = 0,
      |) {
      |${nestedFwSection}
      |   fun header() = apply { $codecName.header(buffer, offset) }
      |   fun blockLength() = $codecName.blockLength(buffer, offset)
      |   fun templateId() = $codecName.templateId(buffer, offset)
      |   fun schemaId() = $codecName.schemaId(buffer, offset)
      |   fun version() = $codecName.version(buffer, offset)
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
      val rawExpr = "$codecName.${field.name}(buffer, offset)"
      val converted = convertFromSbe(field.type, rawExpr)
      val convertedSet = convertToSbe(field.type, field.name)
      "fun ${field.name}() = $converted\n" +
      "fun ${field.name}(${field.name}: ${field.type.name}) = apply { $codecName.${field.name}(buffer, offset, $convertedSet) }"
   }

   return """
      |class ${className}FW(
      |   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray($codecName.SIZE)),
      |   var offset: Int = 0,
      |) {
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
