package sbe4kt.kt

import sbe4kt.KSP
import sbe4kt.ir.*
import sbe4kt.ir.Char
import sbe4kt.ir.Double
import sbe4kt.ir.Enum
import sbe4kt.ir.Float
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate

internal fun KSP.generateCodecSection(message: Message): String {
   val className = message.name
   val blockLength = message.blockLength
   val templateId = message.id
   val schemaId = ir.previousSchema?.schemaId ?: 1
   val schemaVersion = ir.evolution?.newVersion ?: 0

   val hasUnsupportedFields = message.fields.any { !isSupportedCodecType(it.type) }
   if (hasUnsupportedFields) {
      return "// ${className}Codec skipped: contains unsupported field types (not yet supported)"
   }

   val headerConstants = """
      |const val BLOCK_LENGTH = $blockLength
      |const val TEMPLATE_ID = $templateId
      |const val SCHEMA_ID = $schemaId
      |const val SCHEMA_VERSION = $schemaVersion
      |const val HEADER_SIZE = 8
      |
      |const val SIZE = HEADER_SIZE + BLOCK_LENGTH
      |const val PACKED_HEADER = (BLOCK_LENGTH.toLong() and 0xFFFF) or ((TEMPLATE_ID.toLong() and 0xFFFF) shl 16) or ((SCHEMA_ID.toLong() and 0xFFFF) shl 32) or ((SCHEMA_VERSION.toLong() and 0xFFFF) shl 48)""".trimTemplate()

   val headerMethods = """
      |fun readBlockLength(buffer: DirectBuffer, offset: Int = 0): Int = buffer.getShort(offset).toInt() and 0xFFFF
      |fun readTemplateId(buffer: DirectBuffer, offset: Int = 0): Int = buffer.getShort(offset + 2).toInt() and 0xFFFF
      |fun readSchemaId(buffer: DirectBuffer, offset: Int = 0): Int = buffer.getShort(offset + 4).toInt() and 0xFFFF
      |fun readVersion(buffer: DirectBuffer, offset: Int = 0): Int = buffer.getShort(offset + 6).toInt() and 0xFFFF
      |fun writeHeader(buffer: MutableDirectBuffer, offset: Int = 0): Int { buffer.putLong(offset, PACKED_HEADER); return HEADER_SIZE }""".trimTemplate()

   val encodePrefix = "writeHeader(buffer, offset)"
   val decodePrefix = "val tid = readTemplateId(buffer, offset)\n" +
      $$"if (tid != TEMPLATE_ID) throw IllegalStateException(\"Invalid TEMPLATE_ID: $tid, expected: $TEMPLATE_ID\")"

   return generateCodecObject(
      className = className,
      fields = message.fields,
      headerOffset = 8,
      constants = headerConstants,
      headerMethods = headerMethods,
      encodePrefix = encodePrefix,
      decodePrefix = decodePrefix,
   )
}

internal fun generateCompositeCodecSection(struct: Struct): String {
   val hasUnsupportedFields = struct.fields.any { !isSupportedCodecType(it.type) }
   if (hasUnsupportedFields) {
      return "// ${struct.name}Codec skipped: contains unsupported field types"
   }

   return generateCodecObject(
      className = struct.name,
      fields = struct.fields,
      headerOffset = 0,
      constants = "const val SIZE = ${struct.encodedLength}",
   )
}

private fun generateCodecObject(
   className: String,
   fields: List<Field>,
   headerOffset: Int,
   constants: String,
   headerMethods: String? = null,
   encodePrefix: String? = null,
   decodePrefix: String? = null,
): String {
   val fieldsDecOrder = fields.sortedBy { it.declarationOrder }
   val fieldsSbeOrder = fields.sortedBy { it.sbeOrder }

   val relativeOffsets = fieldsDecOrder.joinToString("\n") { field ->
      val fieldUpper = field.name.uppercase()
      "const val RELATIVE_OFFSET_${fieldUpper}=${headerOffset + field.offset}"
   }

   val offsetFns = fieldsDecOrder.joinToString("\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      val fieldUpper = field.name.uppercase()
      "fun offset${fieldCap}(offset: Int = 0) = offset + RELATIVE_OFFSET_${fieldUpper}"
   }

   val simpleFields = fieldsDecOrder.filter { it.type !is Struct && it.type !is Message && it.type !is Union && it.type !is ArrayN && it.type !is StringN }

   val fieldAccessors = simpleFields.joinToString("\n\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      val sbeType = sbeKotlinType(field.type)
      "fun read${fieldCap}(buffer: DirectBuffer, offset: Int = 0): $sbeType = ${
         sbeGet(field.type, "offset${fieldCap}(offset)")
      }\n" + "fun write${fieldCap}(buffer: MutableDirectBuffer, offset: Int = 0, value: $sbeType): Int { ${
         sbePut(field.type, "offset${fieldCap}(offset)", "value")
      }; return ${field.type.size} }"
   }

   val encodeBody = fieldsSbeOrder.joinToString("\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      when (field.type) {
         is Struct, is Message -> "${field.type.name}Codec.writeFrom(source.${field.name}, buffer, offset${fieldCap}(offset))"
         is Union -> generateUnionEncodeExpr(field.type, field.name, fieldCap)
         is ArrayN -> if (field.type.isStruct)
            "for (i in 0 until ${field.type.length}) ${field.type.structType.name}Codec.writeFrom(source.${field.name}[i], buffer, offset${fieldCap}(offset) + i * ${field.type.elementType.size})"
         else
            "${field.type.codecName}.encodeFrom(source.${field.name}, 0, ${field.type.length}, buffer, offset${fieldCap}(offset))"
         is StringN -> "CharArrayCodec.encodeFromCharSequence(source.${field.name}, 0, branchlessMin(source.${field.name}.length, ${field.type.length}), buffer, offset${fieldCap}(offset))"
         else -> "write${fieldCap}(buffer, offset, ${convertToSbe(field.type, "source.${field.name}")})"
      }
   }

   val decodeBody = fieldsSbeOrder.joinToString("\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      when (field.type) {
         is Struct, is Message -> "${field.type.name}Codec.readInto(target.${field.name}, buffer, offset${fieldCap}(offset))"
         is Union -> generateUnionDecodeExpr(field.type, field.name, fieldCap)
         is ArrayN -> if (field.type.isStruct)
            "for (i in 0 until ${field.type.length}) ${field.type.structType.name}Codec.readInto(target.${field.name}[i], buffer, offset${fieldCap}(offset) + i * ${field.type.elementType.size})"
         else
            "${field.type.codecName}.decodeInto(target.${field.name}, 0, ${field.type.length}, buffer, offset${fieldCap}(offset))"
         is StringN -> "target.${field.name} = StringBuilder(${field.type.length}).also { CharArrayCodec.decodeIntoAppendable(it, ${field.type.length}, buffer, offset${fieldCap}(offset)) }.toString()"
         else -> "target.${field.name} = ${convertFromSbe(field.type, "read${fieldCap}(buffer, offset)")}"
      }
   }

   val headerMethodsBlock = if (headerMethods != null) "\n${headerMethods.indent(3)}\n" else ""
   val fullEncodeBody = listOfNotNull(encodePrefix, encodeBody.ifEmpty { null }).joinToString("\n")
   val fullDecodeBody = listOfNotNull(decodePrefix, decodeBody.ifEmpty { null }).joinToString("\n")

   return """
      |object ${className}Codec {
      |${constants.indent(3)}
      |
      |${relativeOffsets.indent(3)}
      |
      |${offsetFns.indent(3)}
      |${headerMethodsBlock}
      |${fieldAccessors.indent(3)}
      |
      |   fun writeFrom(source: $className, buffer: MutableDirectBuffer, offset: Int = 0): Int {
      |${fullEncodeBody.indent(6)}
      |      return SIZE
      |   }
      |
      |   fun readInto(target: $className, buffer: DirectBuffer, offset: Int = 0): Int {
      |${fullDecodeBody.indent(6)}
      |      return SIZE
      |   }
      |
      |   fun size(): Int = SIZE
      |}
      |""".trimTemplate()
}

// --- union encode/decode helpers for message codec ---

private fun generateUnionEncodeExpr(union: Union, fieldName: String, fieldCap: String): String {
   val sb = StringBuilder()
   sb.appendLine("when (val value = source.$fieldName) {")
   for (type in union.allowedTypes) {
      sb.appendLine("   is ${type.name} -> ${type.name}Codec.writeFrom(value, buffer, offset${fieldCap}(offset))")
   }
   if (union.typeName == "Any") {
      sb.appendLine($$"   else -> throw IllegalArgumentException(\"Unexpected type in union field '$$fieldName': ${value::class.qualifiedName}\")")
   }
   sb.append("}")
   return sb.toString()
}

private fun generateUnionDecodeExpr(union: Union, fieldName: String, fieldCap: String): String {
   val sb = StringBuilder()
   sb.appendLine("target.$fieldName = when (readTemplateId(buffer, offset${fieldCap}(offset))) {")
   for (type in union.allowedTypes) {
      sb.appendLine("   ${type.name}Codec.TEMPLATE_ID -> new${type.name}().also { ${type.name}Codec.readInto(it, buffer, offset${fieldCap}(offset)) }")
   }
   sb.appendLine("   else -> throw IllegalStateException(\"Unknown templateId in union field '$fieldName'\")")
   sb.append("}")
   return sb.toString()
}

internal fun isSupportedCodecType(type: IR.Leaf): Boolean = when (type) {
   is Int64, is Int32, is Int16, is Int8 -> true
   is Float, is Double -> true
   is Bool, is Char -> true
   is Enum -> true
   is Value -> isSupportedCodecType(type.underlyingType)
   is Tags -> isSupportedCodecType(type.valueType)
   is Str64Value -> true
   is ArrayN -> if (type.isStruct) type.structType.fields.all { isSupportedCodecType(it.type) } else true
   is StringN -> true
   is Struct -> type.fields.all { isSupportedCodecType(it.type) }
   is Message -> type.fields.all { isSupportedCodecType(it.type) }
   is Union -> type.allowedTypes.all { msg -> msg.fields.all { isSupportedCodecType(it.type) } }
}

// --- raw SBE type mapping ---

private fun sbeKotlinType(type: IR.Leaf): String = when (type) {
   is Int64 -> "Long"
   is Int32 -> "Int"
   is Int16 -> "Short"
   is Int8 -> "Byte"
   is Float -> "Float"
   is Double -> "Double"
   is Bool -> "Byte"
   is Char -> "Byte"
   is Enum -> "Byte"
   is Value -> sbeKotlinType(type.underlyingType)
   is Tags -> sbeKotlinType(type.valueType)
   is Str64Value -> "Long"
   else -> throw UnsupportedOperationException("sbeKotlinType not supported for ${type::class.simpleName}")
}

private fun sbeGet(type: IR.Leaf, pos: String): String = when (type) {
   is Int64 -> "buffer.getLong($pos)"
   is Int32 -> "buffer.getInt($pos)"
   is Int16 -> "buffer.getShort($pos)"
   is Int8, is Bool, is Char, is Enum -> "buffer.getByte($pos)"
   is Float -> "buffer.getFloat($pos)"
   is Double -> "buffer.getDouble($pos)"
   is Value -> sbeGet(type.underlyingType, pos)
   is Tags -> sbeGet(type.valueType, pos)
   is Str64Value -> "buffer.getLong($pos)"
   else -> throw UnsupportedOperationException("sbeGet not supported for ${type::class.simpleName}")
}

private fun sbePut(type: IR.Leaf, pos: String, valueExpr: String): String = when (type) {
   is Int64 -> "buffer.putLong($pos, $valueExpr)"
   is Int32 -> "buffer.putInt($pos, $valueExpr)"
   is Int16 -> "buffer.putShort($pos, $valueExpr)"
   is Int8, is Bool, is Char, is Enum -> "buffer.putByte($pos, $valueExpr)"
   is Float -> "buffer.putFloat($pos, $valueExpr)"
   is Double -> "buffer.putDouble($pos, $valueExpr)"
   is Value -> sbePut(type.underlyingType, pos, valueExpr)
   is Tags -> sbePut(type.valueType, pos, valueExpr)
   is Str64Value -> "buffer.putLong($pos, $valueExpr)"
   else -> throw UnsupportedOperationException("sbePut not supported for ${type::class.simpleName}")
}

internal fun convertToSbe(type: IR.Leaf, valueExpr: String): String = when (type) {
   is Int64, is Int32, is Int16, is Int8, is Float, is Double -> valueExpr
   is Bool -> "$valueExpr.convertToByte()"
   is Char -> "$valueExpr.code.toByte()"
   is Enum -> "$valueExpr.ordinal.toUByte().toByte()"
   is Value -> convertToSbe(type.underlyingType, "$valueExpr.${type.innerName}")
   is Tags -> convertToSbe(type.valueType, valueExpr)
   is Str64Value -> "$valueExpr.value"
   is ArrayN -> valueExpr
   is StringN -> valueExpr
   else -> throw UnsupportedOperationException("convertToSbe not supported for ${type::class.simpleName}")
}

internal fun convertFromSbe(type: IR.Leaf, valueExpr: String): String = when (type) {
   is Int64, is Int32, is Int16, is Int8, is Float, is Double -> valueExpr
   is Bool -> "$valueExpr.convertToBoolean()"
   is Char -> "$valueExpr.toInt().toChar()"
   is Enum -> "${type.name}.entries[$valueExpr.toUByte().toInt()]"
   is Value -> "${type.name}(${convertFromSbe(type.underlyingType, valueExpr)})"
   is Tags -> convertFromSbe(type.valueType, valueExpr)
   is Str64Value -> "${type.name}($valueExpr)"
   is ArrayN -> valueExpr
   is StringN -> valueExpr
   else -> throw UnsupportedOperationException("convertFromSbe not supported for ${type::class.simpleName}")
}
