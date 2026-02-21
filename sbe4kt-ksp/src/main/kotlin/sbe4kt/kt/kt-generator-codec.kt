package sbe4kt.kt

import sbe4kt.META.Encoding
import sbe4kt.ir.*
import sbe4kt.ir.Char
import sbe4kt.ir.Double
import sbe4kt.ir.Enum
import sbe4kt.ir.Float
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate

internal fun generateCodecSection(message: Message, schemaVersion: Int, schemaId: Int): String {
   val className = message.name
   val blockLength = message.blockLength
   val templateId = message.id

   // skip codec generation for messages with unsupported field types
   val hasUnsupportedFields = message.fields.any { !isSupportedCodecType(it.type) }
   if (hasUnsupportedFields) {
      return "// ${className}Codec skipped: contains composite, nested message, or union fields (not yet supported)"
   }

   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }
   val fieldsSbeOrder = message.fields.sortedBy { it.sbeOrder }

   val packedHeader = (blockLength.toLong() and 0xFFFF) or
      ((templateId.toLong() and 0xFFFF) shl 16) or
      ((schemaId.toLong() and 0xFFFF) shl 32) or
      ((schemaVersion.toLong() and 0xFFFF) shl 48)

   val relativeOffsets = fieldsDecOrder.joinToString("\n") { field ->
      val fieldUpper = field.name.uppercase()
      "const val RELATIVE_OFFSET_${fieldUpper}=${8 + field.offset}"
   }

   val offsetFns = fieldsDecOrder.joinToString("\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      val fieldUpper = field.name.uppercase()
      "fun offset${fieldCap}(offset: Int) = offset + RELATIVE_OFFSET_${fieldUpper}"
   }

   val simpleFields = fieldsDecOrder.filter { it.type !is Struct && it.type !is Message && it.type !is Union }

   val fieldAccessors = simpleFields.joinToString("\n\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      val sbeType = sbeKotlinType(field.type)
      "fun ${field.name}(buffer: DirectBuffer, offset: Int): $sbeType = ${
         sbeGet(
            field.type, "offset${fieldCap}(offset)"
         )
      }\n" + "fun ${field.name}(buffer: MutableDirectBuffer, offset: Int, value: $sbeType) { ${
         sbePut(
            field.type, "offset${fieldCap}(offset)", "value"
         )
      } }"
   }

   // encodeFrom/decodeInto: simple fields use local accessors, composite/nested/union fields delegate
   val encodeBody = fieldsSbeOrder.joinToString("\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      when (field.type) {
         is Struct, is Message -> "${field.type.name}Codec.encodeFrom(source.${field.name}, buffer, offset${fieldCap}(offset))"
         is Union -> generateUnionEncodeExpr(field.type as Union, field.name, fieldCap)
         else -> "${field.name}(buffer, offset, ${convertToSbe(field.type, "source.${field.name}")})"
      }
   }

   val decodeBody = fieldsSbeOrder.joinToString("\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      when (field.type) {
         is Struct, is Message -> "${field.type.name}Codec.decodeInto(target.${field.name}, buffer, offset${fieldCap}(offset))"
         is Union -> generateUnionDecodeExpr(field.type as Union, field.name, fieldCap)
         else -> "target.${field.name} = ${convertFromSbe(field.type, "${field.name}(buffer, offset)")}"
      }
   }

   return """
      |object ${className}Codec {
      |   const val BLOCK_LENGTH = $blockLength
      |   const val TEMPLATE_ID = $templateId
      |   const val SCHEMA_ID = $schemaId
      |   const val SCHEMA_VERSION = $schemaVersion
      |   const val HEADER_SIZE = 8
      |
      |   const val SIZE = HEADER_SIZE + BLOCK_LENGTH
      |   const val PACKED_HEADER = ${packedHeader}L
      |
      |${relativeOffsets.indent(3)}
      |
      |   val BYTE_ORDER: java.nio.ByteOrder = java.nio.ByteOrder.LITTLE_ENDIAN
      |
      |${offsetFns.indent(3)}
      |
      |   fun blockLength(buffer: DirectBuffer, offset: Int): Int = buffer.getShort(offset, BYTE_ORDER).toInt() and 0xFFFF
      |   fun templateId(buffer: DirectBuffer, offset: Int): Int = buffer.getShort(offset + 2, BYTE_ORDER).toInt() and 0xFFFF
      |   fun schemaId(buffer: DirectBuffer, offset: Int): Int = buffer.getShort(offset + 4, BYTE_ORDER).toInt() and 0xFFFF
      |   fun version(buffer: DirectBuffer, offset: Int): Int = buffer.getShort(offset + 6, BYTE_ORDER).toInt() and 0xFFFF
      |   fun header(buffer: MutableDirectBuffer, offset: Int) { buffer.putLong(offset, PACKED_HEADER, BYTE_ORDER) }
      |
      |${fieldAccessors.indent(3)}
      |
      |   fun encodeFrom(source: $className, buffer: MutableDirectBuffer, offset: Int) {
      |      header(buffer, offset)
      |${encodeBody.indent(6)}
      |   }
      |
      |   fun decodeInto(target: $className, buffer: DirectBuffer, offset: Int) {
      |      val tid = templateId(buffer, offset)
      |      if (tid != TEMPLATE_ID) throw IllegalStateException("Invalid TEMPLATE_ID: ${'$'}tid, expected: ${'$'}TEMPLATE_ID")
      |${decodeBody.indent(6)}
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
      sb.appendLine("   is ${type.name} -> ${type.name}Codec.encodeFrom(value, buffer, offset${fieldCap}(offset))")
   }
   if (union.typeName == "Any") {
      sb.appendLine("   else -> throw IllegalArgumentException(\"Unexpected type in union field '$fieldName': \${value::class.qualifiedName}\")")
   }
   sb.append("}")
   return sb.toString()
}

private fun generateUnionDecodeExpr(union: Union, fieldName: String, fieldCap: String): String {
   val sb = StringBuilder()
   sb.appendLine("target.$fieldName = when (templateId(buffer, offset${fieldCap}(offset))) {")
   for (type in union.allowedTypes) {
      sb.appendLine("   ${type.name}Codec.TEMPLATE_ID -> new${type.name}().also { ${type.name}Codec.decodeInto(it, buffer, offset${fieldCap}(offset)) }")
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
   is Str64Value -> true
   is ByteArrayN -> true
   is StringN -> true
   is Struct -> type.fields.all { isSupportedCodecType(it.type) }
   is Message -> type.fields.all { isSupportedCodecType(it.type) }
   is Union -> type.allowedTypes.all { msg -> msg.fields.all { isSupportedCodecType(it.type) } }
}

// --- composite codec generation (no header, flat fields) ---

internal fun generateCompositeCodecSection(struct: Struct): String {
   val className = struct.name
   val encodedLength = struct.encodedLength

   val hasUnsupportedFields = struct.fields.any { !isSupportedCodecType(it.type) }
   if (hasUnsupportedFields) {
      return "// ${className}Codec skipped: contains unsupported field types"
   }

   val fieldsDecOrder = struct.fields.sortedBy { it.declarationOrder }
   val fieldsSbeOrder = struct.fields.sortedBy { it.sbeOrder }

   val relativeOffsets = fieldsDecOrder.joinToString("\n") { field ->
      val fieldUpper = field.name.uppercase()
      "const val RELATIVE_OFFSET_${fieldUpper}=${field.offset}"
   }

   val offsetFns = fieldsDecOrder.joinToString("\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      val fieldUpper = field.name.uppercase()
      "fun offset${fieldCap}(offset: Int) = offset + RELATIVE_OFFSET_${fieldUpper}"
   }

   val fieldAccessors = fieldsDecOrder.joinToString("\n\n") { field ->
      val fieldCap = field.name.replaceFirstChar { it.uppercase() }
      val sbeType = sbeKotlinType(field.type)
      "fun ${field.name}(buffer: DirectBuffer, offset: Int): $sbeType = ${
         sbeGet(
            field.type, "offset${fieldCap}(offset)"
         )
      }\n" + "fun ${field.name}(buffer: MutableDirectBuffer, offset: Int, value: $sbeType) { ${
         sbePut(
            field.type, "offset${fieldCap}(offset)", "value"
         )
      } }"
   }

   val encodeBody = fieldsSbeOrder.joinToString("\n") { field ->
      "${field.name}(buffer, offset, ${convertToSbe(field.type, "source.${field.name}")})"
   }

   val decodeBody = fieldsSbeOrder.joinToString("\n") { field ->
      "target.${field.name} = ${convertFromSbe(field.type, "${field.name}(buffer, offset)")}"
   }

   return """
      |object ${className}Codec {
      |   const val SIZE = $encodedLength
      |
      |${relativeOffsets.indent(3)}
      |
      |   val BYTE_ORDER: java.nio.ByteOrder = java.nio.ByteOrder.LITTLE_ENDIAN
      |
      |${offsetFns.indent(3)}
      |
      |${fieldAccessors.indent(3)}
      |
      |   fun encodeFrom(source: $className, buffer: MutableDirectBuffer, offset: Int) {
      |${encodeBody.indent(6)}
      |   }
      |
      |   fun decodeInto(target: $className, buffer: DirectBuffer, offset: Int) {
      |${decodeBody.indent(6)}
      |   }
      |
      |   fun size(): Int = SIZE
      |}
      |""".trimTemplate()
}

// --- raw SBE type mapping ---

// returns the Kotlin type that matches the SBE primitive (what SBE encoder/decoder uses)
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
   is Str64Value -> "Long"
   is ByteArrayN -> "ByteArray"
   is StringN -> "String"
   else -> throw UnsupportedOperationException("sbeKotlinType not supported for ${type::class.simpleName}")
}

// --- raw buffer read (no conversion, same as SBE decoder) ---

private fun sbeGet(type: IR.Leaf, pos: String): String = when (type) {
   is Int64 -> "buffer.getLong($pos, BYTE_ORDER)"
   is Int32 -> "buffer.getInt($pos, BYTE_ORDER)"
   is Int16 -> "buffer.getShort($pos, BYTE_ORDER)"
   is Int8, Bool, Char, is Enum -> "buffer.getByte($pos)"
   is Float -> "buffer.getFloat($pos, BYTE_ORDER)"
   is Double -> "buffer.getDouble($pos, BYTE_ORDER)"
   is Value -> sbeGet(type.underlyingType, pos)
   is Str64Value -> "buffer.getLong($pos, BYTE_ORDER)"
   is ByteArrayN -> "ByteArray(${type.length}).also { buffer.getBytes($pos, it, 0, ${type.length}) }"
   is StringN -> when (type.encoding) {
      Encoding.ASCII -> "buffer.getStringWithoutLengthAscii($pos, ${type.length}).trimEnd('\\u0000')"
      Encoding.UTF8 -> "buffer.getStringWithoutLengthUtf8($pos, ${type.length}).trimEnd('\\u0000')"
   }
   else -> throw UnsupportedOperationException("sbeGet not supported for ${type::class.simpleName}")
}

// --- raw buffer write (no conversion, same as SBE encoder) ---

private fun sbePut(type: IR.Leaf, pos: String, valueExpr: String): String = when (type) {
   is Int64 -> "buffer.putLong($pos, $valueExpr, BYTE_ORDER)"
   is Int32 -> "buffer.putInt($pos, $valueExpr, BYTE_ORDER)"
   is Int16 -> "buffer.putShort($pos, $valueExpr, BYTE_ORDER)"
   is Int8, Bool, Char, is Enum -> "buffer.putByte($pos, $valueExpr)"
   is Float -> "buffer.putFloat($pos, $valueExpr, BYTE_ORDER)"
   is Double -> "buffer.putDouble($pos, $valueExpr, BYTE_ORDER)"
   is Value -> sbePut(type.underlyingType, pos, valueExpr)
   is Str64Value -> "buffer.putLong($pos, $valueExpr, BYTE_ORDER)"
   is ByteArrayN -> "buffer.putBytes($pos, $valueExpr, 0, ${type.length})"
   is StringN -> when (type.encoding) {
      Encoding.ASCII -> "buffer.putStringWithoutLengthAscii($pos, $valueExpr)"
      Encoding.UTF8 -> "buffer.putStringWithoutLengthUtf8($pos, $valueExpr)"
   }
   else -> throw UnsupportedOperationException("sbePut not supported for ${type::class.simpleName}")
}

// --- Kotlin type → SBE primitive conversion (used in encodeFrom) ---

internal fun convertToSbe(type: IR.Leaf, valueExpr: String): String = when (type) {
   is Int64, is Int32, is Int16, is Int8, is Float, is Double -> valueExpr
   is Bool -> "$valueExpr.convertToByte()"
   is Char -> "$valueExpr.code.toByte()"
   is Enum -> "$valueExpr.ordinal.toUByte().toByte()"
   is Value -> convertToSbe(type.underlyingType, "$valueExpr.${type.innerName}")
   is Str64Value -> "$valueExpr.value"
   is ByteArrayN -> valueExpr
   is StringN -> valueExpr
   else -> throw UnsupportedOperationException("convertToSbe not supported for ${type::class.simpleName}")
}

// --- SBE primitive → Kotlin type conversion (used in decodeInto) ---

internal fun convertFromSbe(type: IR.Leaf, valueExpr: String): String = when (type) {
   is Int64, is Int32, is Int16, is Int8, is Float, is Double -> valueExpr
   is Bool -> "$valueExpr.convertToBoolean()"
   is Char -> "$valueExpr.toInt().toChar()"
   is Enum -> "${type.name}.entries[$valueExpr.toUByte().toInt()]"
   is Value -> "${type.name}(${convertFromSbe(type.underlyingType, valueExpr)})"
   is Str64Value -> "${type.name}($valueExpr)"
   is ByteArrayN -> valueExpr
   is StringN -> valueExpr
   else -> throw UnsupportedOperationException("convertFromSbe not supported for ${type::class.simpleName}")
}
