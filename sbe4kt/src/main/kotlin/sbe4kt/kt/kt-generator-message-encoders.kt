package sbe4kt.kt

import sbe4kt.ir.Message
import sbe4kt.util.indent
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateEncodersSection(message: Message): String {
   val className = message.name

   // Use allFields for constructor params (includes boolean fields even if useFlags=true)
   val fieldsDecOrder = message.allFields.sortedBy { it.declarationOrder }
   val fieldsSbeOrder = message.fields.sortedBy { it.sbeOrder }

   val encoderName = className.replaceFirstChar { it.lowercase() } + "Encoder"
   val decoderName = className.replaceFirstChar { it.lowercase() } + "Decoder"
   val sourceVarName = "source"
   val targetVarName = "target"

   val sizeConstName = className.sbeSizeConstant()
   val totalSize = message.blockLength

   val defaultParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.defaultValue}," }
   val randomParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.randomValue}," }
   val args = fieldsDecOrder.joinToString(", ") { it.name }

   // Encoder body: non-boolean fields + flags
   val encoderBody = if (message.useFlags && message.booleanFields.isNotEmpty()) {
      val nonBoolEncoding = fieldsSbeOrder.joinToString("\n") { it.type.howToEncodeFromSource(encoderName, it.name, "source") }
      val flagsChain = message.booleanFields.joinToString("") { "\n   .${it.name}($sourceVarName.${it.name})" }
      "$nonBoolEncoding\n$encoderName.flags().clear()$flagsChain"
   } else {
      fieldsSbeOrder.joinToString("\n") { it.type.howToEncodeFromSource(encoderName, it.name, "source") }
   }

   // Decoder body: non-boolean fields + flags
   val decoderBody = if (message.useFlags && message.booleanFields.isNotEmpty()) {
      val nonBoolDecoding = fieldsSbeOrder.joinToString("\n") { it.type.howToDecodeIntoTarget(decoderName, it.name, "target") }
      val flagsDecoding = message.booleanFields.joinToString("\n") { "$targetVarName.${it.name} = $decoderName.flags().${it.name}()" }
      "$nonBoolDecoding\n$flagsDecoding"
   } else {
      fieldsSbeOrder.joinToString("\n") { it.type.howToDecodeIntoTarget(decoderName, it.name, "target") }
   }

   return """
      |const val $sizeConstName = MessageHeaderEncoder.ENCODED_LENGTH + $totalSize
      |
      |@JvmOverloads
      |fun new$className(
      |${defaultParams.indent(3)}
      |): $className {
      |   return $className($args)
      |}
      |
      |@JvmOverloads
      |fun random$className(
      |${randomParams.indent(3)}
      |): $className {
      |   return $className($args)
      |}
      |
      |fun encode$className($sourceVarName: $className, buffer: MutableDirectBuffer, offset: Int = 0): Int {
      |   val headerEncoder = MessageHeaderEncoder()
      |   val $encoderName = ${className}Encoder()
      |   $encoderName.wrapAndApplyHeader(buffer, offset, headerEncoder)
      |${encoderBody.indent(3)}
      |   return MessageHeaderEncoder.ENCODED_LENGTH + $encoderName.encodedLength()
      |}
      |
      |fun decode$className($targetVarName: $className, buffer: DirectBuffer, offset: Int = 0): Int {
      |   val headerDecoder = MessageHeaderDecoder()
      |   val $decoderName = ${className}Decoder()
      |   $decoderName.wrapAndApplyHeader(buffer, offset, headerDecoder)
      |${decoderBody.indent(3)}
      |   return MessageHeaderDecoder.ENCODED_LENGTH + $decoderName.encodedLength()
      |}
      |
      |fun $className.encodeTo(buffer: MutableDirectBuffer, offset: Int = 0): Int = encode$className(this, buffer, offset)
      |fun $className.decodeFrom(buffer: DirectBuffer, offset: Int = 0): Int = decode$className(this, buffer, offset)
      |""".trimTemplate()
}