package sbe4kt.kt

import sbe4kt.ir.Message
import sbe4kt.util.indent
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateEncodersSection(message: Message): String {
   val className = message.name
   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }
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

   val encoderBody = fieldsSbeOrder.joinToString("\n") { it.type.howToEncodeFromSource(encoderName, it.name, "source") }
   val decoderBody = fieldsSbeOrder.joinToString("\n") { it.type.howToDecodeIntoTarget(decoderName, it.name, "target") }

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
      |   return ${className}RW(buffer, offset).encodeFrom($sourceVarName)
      |}
      |
      |fun decode$className($targetVarName: $className, buffer: MutableDirectBuffer, offset: Int = 0): Int {
      |   return ${className}RW(buffer, offset).decodeInto($targetVarName)
      |}
      |
      |fun $className.encodeTo(buffer: MutableDirectBuffer, offset: Int = 0): Int = encode$className(this, buffer, offset)
      |fun $className.decodeFrom(buffer: MutableDirectBuffer, offset: Int = 0): Int = decode$className(this, buffer, offset)
      |""".trimTemplate()
}