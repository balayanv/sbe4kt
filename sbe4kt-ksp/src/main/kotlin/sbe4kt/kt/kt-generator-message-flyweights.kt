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

   // pre-allocated FWs for composite, nested message, and union variant fields
   val nestedRwFields = mutableListOf<String>()
   val nestedWraps = mutableListOf<String>()

   for (field in fieldsDecOrder) {
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      val fieldUpper = field.name.uppercase()
      when (field.type) {
         is Struct, is sbe4kt.ir.Message -> {
            val rwVar = "${field.name}FW"
            nestedRwFields.add("private val $rwVar = ${field.type.name}FW()")
            nestedWraps.add("$rwVar.wrap(buffer, $codecName.RELATIVE_OFFSET_${fieldUpper}, ${field.type.name}Codec.size())")
         }
         is ByteArrayN -> {
            val rwVar = "${field.name}FW"
            nestedRwFields.add("private val $rwVar = ByteArrayFW(${field.type.length})")
            nestedWraps.add("$rwVar.wrap(buffer, $codecName.RELATIVE_OFFSET_${fieldUpper}, ${field.type.size})")
         }
         is ShortArrayN -> {
            val rwVar = "${field.name}FW"
            nestedRwFields.add("private val $rwVar = ShortArrayFW(${field.type.length})")
            nestedWraps.add("$rwVar.wrap(buffer, $codecName.RELATIVE_OFFSET_${fieldUpper}, ${field.type.size})")
         }
         is IntArrayN -> {
            val rwVar = "${field.name}FW"
            nestedRwFields.add("private val $rwVar = IntArrayFW(${field.type.length})")
            nestedWraps.add("$rwVar.wrap(buffer, $codecName.RELATIVE_OFFSET_${fieldUpper}, ${field.type.size})")
         }
         is LongArrayN -> {
            val rwVar = "${field.name}FW"
            nestedRwFields.add("private val $rwVar = LongArrayFW(${field.type.length})")
            nestedWraps.add("$rwVar.wrap(buffer, $codecName.RELATIVE_OFFSET_${fieldUpper}, ${field.type.size})")
         }
         is FloatArrayN -> {
            val rwVar = "${field.name}FW"
            nestedRwFields.add("private val $rwVar = FloatArrayFW(${field.type.length})")
            nestedWraps.add("$rwVar.wrap(buffer, $codecName.RELATIVE_OFFSET_${fieldUpper}, ${field.type.size})")
         }
         is DoubleArrayN -> {
            val rwVar = "${field.name}FW"
            nestedRwFields.add("private val $rwVar = DoubleArrayFW(${field.type.length})")
            nestedWraps.add("$rwVar.wrap(buffer, $codecName.RELATIVE_OFFSET_${fieldUpper}, ${field.type.size})")
         }
         is CharArrayN -> {
            val charArr = field.type as CharArrayN
            val rwVar = "${field.name}FW"
            nestedRwFields.add("private val $rwVar = CharArrayFW(${charArr.length})")
            nestedWraps.add("$rwVar.wrap(buffer, $codecName.RELATIVE_OFFSET_${fieldUpper}, ${charArr.size})")
         }
         is StringN -> {
            val strN = field.type as StringN
            val rwVar = "${field.name}FW"
            nestedRwFields.add("private val $rwVar = CharArrayFW(${strN.length})")
            nestedWraps.add("$rwVar.wrap(buffer, $codecName.RELATIVE_OFFSET_${fieldUpper}, ${strN.size})")
         }
         is ArrayN -> {
            val structArr = field.type as ArrayN
            val rwVar = "${field.name}FW"
            nestedRwFields.add("private val $rwVar = run { val elementFW = ${structArr.structType.name}FW(); ArrayFW<${structArr.structType.name}FW>(${structArr.length}, ${structArr.structType.name}Codec.SIZE) { buf, off, len -> elementFW.wrap(buf, off, len) } }")
            nestedWraps.add("$rwVar.wrap(buffer, $codecName.RELATIVE_OFFSET_${fieldUpper}, ${structArr.length} * ${structArr.structType.name}Codec.SIZE)")
         }
         is Union -> {
            val union = field.type as Union
            for (variant in union.allowedTypes) {
               val variantLower = variant.name.replaceFirstChar { it.lowercase() }
               nestedRwFields.add("private val ${variantLower}FW = ${variant.name}FW()")
               nestedWraps.add("${variantLower}FW.wrap(buffer, $codecName.RELATIVE_OFFSET_${fieldUpper}, ${variant.name}Codec.size())")
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
            lines.add("fun ${field.name}Id(): Int = $codecName.templateId(buffer, $codecName.offset${fieldCap}())")
            for (variant in union.allowedTypes) {
               val variantLower = variant.name.replaceFirstChar { it.lowercase() }
               val variantCap = variant.name.replaceFirstChar { it.uppercase() }
               lines.add("fun ${field.name}${variantCap}() = ${variantLower}FW")
            }
            val whenBranches = union.allowedTypes.joinToString("\n") { variant ->
               "   is ${variant.name} -> ${variant.name}Codec.encodeFrom(value, buffer, $codecName.offset${fieldCap}())"
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
            val rawExpr = "$codecName.${field.name}(buffer)"
            val converted = convertFromSbe(field.type, rawExpr)
            val convertedSet = convertToSbe(field.type, field.name)
            "fun ${field.name}() = $converted\n" +
            "fun ${field.name}(${field.name}: ${field.type.name}) = apply { $codecName.${field.name}(buffer, 0, $convertedSet) }"
         }
      }
   }

   val nestedRwSection = if (nestedRwFields.isEmpty()) "" else nestedRwFields.joinToString("\n").indent(3) + "\n"

   val wrapSuffix = if (nestedWraps.isEmpty()) "" else "; wrapNested()"
   val wrapNestedSection = if (nestedWraps.isEmpty()) "" else {
      "\n" + ("private fun wrapNested() {\n" + nestedWraps.joinToString("\n").indent(3) + "\n}").indent(3) + "\n"
   }

   val wrapMethods = listOf(
      "fun init() = wrap(ByteArray($codecName.SIZE))",
      "fun wrap(buffer: ByteArray) = apply { this.buffer.wrap(buffer)$wrapSuffix }",
      "fun wrap(buffer: ByteArray, offset: Int, length: Int) = apply { this.buffer.wrap(buffer, offset, length)$wrapSuffix }",
      "fun wrap(buffer: ByteBuffer) = apply { this.buffer.wrap(buffer)$wrapSuffix }",
      "fun wrap(buffer: ByteBuffer, offset: Int, length: Int) = apply { this.buffer.wrap(buffer, offset, length)$wrapSuffix }",
      "fun wrap(buffer: DirectBuffer) = apply { this.buffer.wrap(buffer)$wrapSuffix }",
      "fun wrap(buffer: DirectBuffer, offset: Int) = apply { this.buffer.wrap(buffer, offset, $codecName.size())$wrapSuffix }",
      "fun wrap(buffer: DirectBuffer, offset: Int, length: Int) = apply { this.buffer.wrap(buffer, offset, length)$wrapSuffix }",
      "fun wrap(address: Long, length: Int) = apply { this.buffer.wrap(address, length)$wrapSuffix }",
   ).joinToString("\n")

   return """
      |class ${className}FW {
      |   private val buffer = UnsafeBuffer()
      |${nestedRwSection}
      |   fun header() = apply { $codecName.header(buffer) }
      |   fun blockLength() = $codecName.blockLength(buffer)
      |   fun templateId() = $codecName.templateId(buffer)
      |   fun schemaId() = $codecName.schemaId(buffer)
      |   fun version() = $codecName.version(buffer)
      |
      |${wrapMethods.indent(3)}
      |${wrapNestedSection}
      |${fwAccessors.indent(3)}
      |
      |   fun encodeFrom(source: $className) = apply {
      |      $codecName.encodeFrom(source, buffer)
      |   }
      |
      |   fun decodeInto(target: $className) = apply {
      |      $codecName.decodeInto(target, buffer)
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
      val rawExpr = "$codecName.${field.name}(buffer)"
      val converted = convertFromSbe(field.type, rawExpr)
      val convertedSet = convertToSbe(field.type, field.name)
      "fun ${field.name}() = $converted\n" +
      "fun ${field.name}(${field.name}: ${field.type.name}) = apply { $codecName.${field.name}(buffer, 0, $convertedSet) }"
   }

   val wrapMethods = listOf(
      "fun init() = wrap(ByteArray($codecName.SIZE))",
      "fun wrap(buffer: ByteArray) = apply { this.buffer.wrap(buffer) }",
      "fun wrap(buffer: ByteArray, offset: Int, length: Int) = apply { this.buffer.wrap(buffer, offset, length) }",
      "fun wrap(buffer: ByteBuffer) = apply { this.buffer.wrap(buffer) }",
      "fun wrap(buffer: ByteBuffer, offset: Int, length: Int) = apply { this.buffer.wrap(buffer, offset, length) }",
      "fun wrap(buffer: DirectBuffer) = apply { this.buffer.wrap(buffer) }",
      "fun wrap(buffer: DirectBuffer, offset: Int) = apply { this.buffer.wrap(buffer, offset, $codecName.size()) }",
      "fun wrap(buffer: DirectBuffer, offset: Int, length: Int) = apply { this.buffer.wrap(buffer, offset, length) }",
      "fun wrap(address: Long, length: Int) = apply { this.buffer.wrap(address, length) }",
   ).joinToString("\n")

   return """
      |class ${className}FW {
      |   private val buffer = UnsafeBuffer()
      |
      |${wrapMethods.indent(3)}
      |
      |${fwAccessors.indent(3)}
      |
      |   fun encodeFrom(source: $className) = apply {
      |      $codecName.encodeFrom(source, buffer)
      |   }
      |
      |   fun decodeInto(target: $className) = apply {
      |      $codecName.decodeInto(target, buffer)
      |   }
      |}
      |""".trimTemplate()
}
