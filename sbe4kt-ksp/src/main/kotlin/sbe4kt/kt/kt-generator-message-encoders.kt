package sbe4kt.kt

import sbe4kt.ir.Message
import sbe4kt.util.indent
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateEncodersSection(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()

   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }
   val defaultParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.defaultValue}," }
   val randomParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.randomValue}," }
   val args = fieldsDecOrder.joinToString(", ") { it.name }

   return """
      |const val $sizeConstName = ${if (message.hasVariableFields) "-1" else "MessageHeaderEncoder.ENCODED_LENGTH + ${message.blockLength}"}
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
      |fun encode$className(source: $className, buffer: MutableDirectBuffer, offset: Int = 0): Int {
      |   return ${className}RW(buffer, offset).encodeFrom(source)
      |}
      |
      |fun decode$className(target: $className, buffer: MutableDirectBuffer, offset: Int = 0): Int {
      |   return ${className}RW(buffer, offset).decodeInto(target)
      |}
      |
      |fun $className.encodeTo(buffer: MutableDirectBuffer, offset: Int = 0): Int = encode$className(this, buffer, offset)
      |fun $className.decodeFrom(buffer: MutableDirectBuffer, offset: Int = 0): Int = decode$className(this, buffer, offset)
      |""".trimTemplate()
}
