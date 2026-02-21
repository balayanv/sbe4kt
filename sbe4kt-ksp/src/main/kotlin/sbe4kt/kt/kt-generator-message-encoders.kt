package sbe4kt.kt

import sbe4kt.ir.Message
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate

internal fun generateEncodersSection(message: Message): String {
   val className = message.name
   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }

   val defaultParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.defaultValue}," }
   val randomParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.randomValue}," }
   val args = fieldsDecOrder.joinToString(", ") { it.name }

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
      |""".trimTemplate()
}
