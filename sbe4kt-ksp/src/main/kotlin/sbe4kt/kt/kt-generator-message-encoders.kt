package sbe4kt.kt

import sbe4kt.ir.Message
import sbe4kt.util.trimTemplate

internal fun generateEncodersSection(message: Message): String {
   val className = message.name
   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }

   val defaultParams = fieldsDecOrder.joinToString(",\n") { "   ${it.name}: ${it.type.name} = ${it.type.defaultValue}" }
   val randomParams = fieldsDecOrder.joinToString(",\n") { "   ${it.name}: ${it.type.name} = ${it.type.randomValue}" }
   val args = fieldsDecOrder.joinToString(", ") { it.name }

   return """
      |@JvmOverloads
      |fun new$className(
      |$defaultParams
      |): $className {
      |   return $className($args)
      |}
      |
      |@JvmOverloads
      |fun random$className(
      |$randomParams
      |): $className {
      |   return $className($args)
      |}
      |""".trimTemplate()
}
