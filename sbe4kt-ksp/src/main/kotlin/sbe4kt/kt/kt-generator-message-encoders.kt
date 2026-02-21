package sbe4kt.kt

import sbe4kt.ir.*
import sbe4kt.ir.Enum
import sbe4kt.ir.Float
import sbe4kt.ir.Double
import sbe4kt.ir.Char
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate

internal fun generateEncodersSection(message: Message): String {
   val className = message.name
   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }

   val defaultParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.defaultValue}," }
   val randomParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.randomValue}," }
   val args = fieldsDecOrder.joinToString(", ") { it.name }

   val sbeSection = generateSbeSection(message)

   return """
      |fun new$className(
      |${defaultParams.indent(3)}
      |): $className {
      |   return $className($args)
      |}
      |
      |fun random$className(
      |${randomParams.indent(3)}
      |): $className {
      |   return $className($args)
      |}
      |
      |fun encode$className(source: $className, buffer: MutableDirectBuffer, offset: Int = 0) {
      |   ${className}Codec.encodeFrom(source, buffer, offset)
      |}
      |
      |fun decode$className(target: $className, buffer: DirectBuffer, offset: Int = 0) {
      |   ${className}Codec.decodeInto(target, buffer, offset)
      |}
      |$sbeSection""".trimTemplate()
}

// --- SBE encoder/decoder variant generation ---

private fun generateSbeSection(message: Message): String {
   val className = message.name

   if (message.fields.any { !isSupportedCodecType(it.type) }) return ""

   val fieldsSbeOrder = message.fields.sortedBy { it.sbeOrder }
   val encoderVar = className.replaceFirstChar { it.lowercase() } + "Encoder"
   val decoderVar = className.replaceFirstChar { it.lowercase() } + "Decoder"

   val encodeBody = fieldsSbeOrder.joinToString("\n") { sbeEncodeField(it, encoderVar, "source", className) }
   val decodeBody = fieldsSbeOrder.joinToString("\n") { sbeDecodeField(it, decoderVar, "target", className) }

   return """
      |
      |fun encode${className}SBE(source: $className, buffer: MutableDirectBuffer, offset: Int = 0) {
      |   val headerEncoder = sbe4kt.MessageHeaderEncoder()
      |   val $encoderVar = sbe4kt.${className}Encoder()
      |   $encoderVar.wrapAndApplyHeader(buffer, offset, headerEncoder)
      |${encodeBody.indent(3)}
      |}
      |
      |fun decode${className}SBE(target: $className, buffer: DirectBuffer, offset: Int = 0) {
      |   val headerDecoder = sbe4kt.MessageHeaderDecoder()
      |   val $decoderVar = sbe4kt.${className}Decoder()
      |   $decoderVar.wrapAndApplyHeader(buffer, offset, headerDecoder)
      |${decodeBody.indent(3)}
      |}
      |""".trimTemplate()
}

private fun sbeEncodeField(field: Field, encoderVar: String, sourcePrefix: String, parentClassName: String): String {
   val fieldName = field.name
   val type = field.type
   return when (type) {
      is Struct -> {
         val sb = StringBuilder()
         sb.appendLine("val $fieldName = $encoderVar.$fieldName()")
         for (sf in type.fields.sortedBy { it.sbeOrder }) {
            sb.appendLine("$fieldName.${sf.name}(${sf.type.convertToSbe("$sourcePrefix.$fieldName.${sf.name}")})")
         }
         sb.removeSuffix("\n").toString()
      }
      is Message -> {
         val encoderClass = encoderVar.replaceFirstChar { it.uppercase() }
         "encode${type.name}SBE($sourcePrefix.$fieldName, buffer, offset + sbe4kt.MessageHeaderEncoder.ENCODED_LENGTH + sbe4kt.${encoderClass}.${fieldName}EncodingOffset())"
      }
      is Union -> {
         val encoderClass = encoderVar.replaceFirstChar { it.uppercase() }
         val sb = StringBuilder()
         sb.appendLine("when (val ${fieldName}Val = $sourcePrefix.$fieldName) {")
         for (variant in type.allowedTypes) {
            sb.appendLine("   is ${variant.name} -> encode${variant.name}SBE(${fieldName}Val, buffer, offset + sbe4kt.MessageHeaderEncoder.ENCODED_LENGTH + sbe4kt.${encoderClass}.${fieldName}EncodingOffset())")
         }
         if (type.typeName == "Any") {
            sb.appendLine("   else -> throw IllegalArgumentException(\"Unexpected type in union field '$fieldName': \${${fieldName}Val::class.qualifiedName}\")")
         }
         sb.append("}")
         sb.toString()
      }
      is ByteArrayN -> {
         val fieldCap = fieldName.replaceFirstChar { it.uppercase() }
         "$encoderVar.put$fieldCap($sourcePrefix.$fieldName, 0, ${type.length})"
      }
      is StringN -> "$encoderVar.$fieldName($sourcePrefix.$fieldName)"
      else -> "$encoderVar.$fieldName(${type.convertToSbe("$sourcePrefix.$fieldName")})"
   }
}

private fun sbeDecodeField(field: Field, decoderVar: String, targetPrefix: String, parentClassName: String): String {
   val fieldName = field.name
   val type = field.type
   return when (type) {
      is Struct -> {
         val sb = StringBuilder()
         sb.appendLine("val $fieldName = $decoderVar.$fieldName()")
         for (sf in type.fields.sortedBy { it.sbeOrder }) {
            sb.appendLine("$targetPrefix.$fieldName.${sf.name} = ${sf.type.convertFromSbe("$fieldName.${sf.name}()")}")
         }
         sb.removeSuffix("\n").toString()
      }
      is Message -> {
         val decoderClass = decoderVar.replaceFirstChar { it.uppercase() }
         "decode${type.name}SBE($targetPrefix.$fieldName, buffer, offset + sbe4kt.MessageHeaderDecoder.ENCODED_LENGTH + sbe4kt.${decoderClass}.${fieldName}EncodingOffset())"
      }
      is Union -> {
         val decoderClass = decoderVar.replaceFirstChar { it.uppercase() }
         val sb = StringBuilder()
         val offsetExpr = "offset + sbe4kt.MessageHeaderDecoder.ENCODED_LENGTH + sbe4kt.${decoderClass}.${fieldName}EncodingOffset()"
         sb.appendLine("run {")
         sb.appendLine("   val ${fieldName}Hdr = sbe4kt.MessageHeaderDecoder()")
         sb.appendLine("   ${fieldName}Hdr.wrap(buffer, $offsetExpr)")
         sb.appendLine("   $targetPrefix.$fieldName = when (${fieldName}Hdr.templateId()) {")
         for (variant in type.allowedTypes) {
            sb.appendLine("      sbe4kt.${variant.name}Encoder.TEMPLATE_ID -> new${variant.name}().also { decode${variant.name}SBE(it, buffer, $offsetExpr) }")
         }
         sb.appendLine("      else -> throw IllegalStateException(\"Unknown templateId in union field '$fieldName': \${${fieldName}Hdr.templateId()}\")")
         sb.appendLine("   }")
         sb.append("}")
         sb.toString()
      }
      is ByteArrayN -> {
         val fieldCap = fieldName.replaceFirstChar { it.uppercase() }
         "$decoderVar.get$fieldCap($targetPrefix.$fieldName, 0, ${type.length})"
      }
      is StringN -> "$targetPrefix.$fieldName = $decoderVar.$fieldName()"
      else -> "$targetPrefix.$fieldName = ${type.convertFromSbe("$decoderVar.$fieldName()")}"
   }
}
