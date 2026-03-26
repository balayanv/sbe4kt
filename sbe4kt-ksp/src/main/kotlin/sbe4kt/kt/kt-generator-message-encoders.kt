package sbe4kt.kt

import sbe4kt.ir.Message
import sbe4kt.util.trimTemplate

internal fun generateEncodersSection(message: Message): String {
   val className = message.name
   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }

   val defaultArgs = fieldsDecOrder.joinToString(",\n") { "   ${it.name} = ${it.type.defaultValue}" }
   val randomArgs = fieldsDecOrder.joinToString(",\n") { "   ${it.name} = ${it.type.randomValue}" }

   return """
      |fun new$className() = $className(
      |$defaultArgs
      |)
      |
      |fun random$className() = $className(
      |$randomArgs
      |)
      |""".trimTemplate()
}
