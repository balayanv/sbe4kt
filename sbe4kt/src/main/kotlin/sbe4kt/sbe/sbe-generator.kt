package sbe4kt.sbe

import sbe4kt.KSP
import sbe4kt.ir.ByteArrayN
import sbe4kt.ir.Enum
import sbe4kt.ir.IR
import sbe4kt.ir.Message
import sbe4kt.ir.StringN
import sbe4kt.ir.Struct
import sbe4kt.ir.Union
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate
import java.io.File

internal fun KSP.generateSbe() {
   val schemaFile = File(resourcesDir, "sbe-schema.xml")
   val byteArrayTypes = ir.byteArraySizes.joinToString("\n") { generateByteArrayType(it) }
   val stringTypes = ir.fixedStrings.joinToString("\n") { generateStringType(it) }
   val enums = ir.enums.joinToString("\n") { generateEnumType(it) }
   val structs = ir.structs.joinToString("\n") { generateCompositeType(it) }
   val unions = ir.unionFields.joinToString("\n") { generateUnionComposite(it) }
   val messages = ir.messages.joinToString("\n") { generateMessageBody(it) }

   val xml = """
      |<?xml version='1.0' encoding='UTF-8'?>
      |<sbe:messageSchema xmlns:sbe='http://fixprotocol.io/2016/sbe'
      |                   package='sbe4kt'
      |                   id='1'
      |                   version='0'
      |                   semanticVersion='5.2'
      |                   description='Consolidated SBE Schema'
      |                   byteOrder='littleEndian'>
      |   <types>
      |      <composite name='messageHeader' description='Message identifiers and length of message root'>
      |         <type name='blockLength' primitiveType='uint16'/>
      |         <type name='templateId' primitiveType='uint16'/>
      |         <type name='schemaId' primitiveType='uint16'/>
      |         <type name='version' primitiveType='uint16'/>
      |      </composite>
      |
      |${byteArrayTypes.indent(6)}
      |
      |${stringTypes.indent(6)}
      |
      |${enums.indent(6)}
      |
      |${structs.indent(6)}
      |
      |${unions.indent(6)}
      |   </types>
      |
      |${messages.indent(3)}
      |
      |</sbe:messageSchema>
      |""".trimTemplate()

   schemaFile.parentFile?.mkdirs()
   schemaFile.writeText(xml)
}

private fun generateMessageBody(message: Message): String {
   val fields = message.fields.sortedBy { it.sbeOrder }

   val xml = fields.joinToString("\n") {
      val offsetAttr = if (message.usePadding && it.offset >= 0) " offset='${it.offset}'" else ""
      val presenceAttr = if (it.isNullable) " presence='optional'" else ""
      "<field name='${it.name}' id='${it.id}' type='${it.type.sbeName}'$offsetAttr$presenceAttr/>"
   }

   val blockLengthAttribute = if (message.usePadding && message.blockLength > 0) {
      " blockLength='${message.blockLength}'"
   } else {
      ""
   }

   return """
      |<sbe:message name='${message.name}' id='${message.id}'$blockLengthAttribute description='${message.name}'>
      |${xml.indent(3)}
      |</sbe:message>
      |""".trimMargin()
}

private fun generateCompositeType(struct: Struct): String {
   val fields = struct.fields.sortedBy { it.sbeOrder }
   val xml = if (struct.usePadding) {
      fields.joinToString("\n") { generateCompositeField(it.name, it.type, it.offset) }
   } else {
      fields.joinToString("\n") { generateCompositeField(it.name, it.type) }
   }

   return """
      |<composite name='${struct.sbeName}'>
      |${xml.indent(3)}
      |</composite>
      |""".trimMargin()
}

private fun generateCompositeField(fieldName: String, type: IR.Leaf, offset: Int = -1): String {
   val isPrimitive = when (type) {
      is StringN, is ByteArrayN, is Enum, is Struct, is Union -> false
      else -> true
   }

   return if (isPrimitive) {
      if (offset >= 0) {
         "<type name='$fieldName' primitiveType='${type.sbeName}' offset='$offset'/>"
      } else {
         "<type name='$fieldName' primitiveType='${type.sbeName}'/>"
      }
   } else {
      if (offset >= 0) {
         "<ref name='$fieldName' type='${type.sbeName}' offset='$offset'/>"
      } else {
         "<ref name='$fieldName' type='${type.sbeName}'/>"
      }
   }
}

private fun generateUnionComposite(union: Union): String {
   return """
      |<composite name='${union.sbeName}'>
      |   <type name='type' primitiveType='int32'/>
      |   <type name='data' primitiveType='uint8' length='${union.maxSize}'/>
      |</composite>
      |""".trimMargin()
}

private fun generateByteArrayType(size: Int) = "<type name='ByteArray$size' primitiveType='uint8' length='$size'/>"

private fun generateStringType(strN: StringN) = "<type name='${strN.sbeName}' primitiveType='char' length='${strN.length}' characterEncoding='${strN.encoding.sbeEncoding}'/>"

private fun generateEnumType(enum: Enum): String {
   val xml = enum.entries
      .mapIndexed { index, name -> "<validValue name='$name'>$index</validValue>" }
      .joinToString("\n")

   return """
      |<enum name='${enum.sbeName}' encodingType='uint8'>
      |${xml.indent(3)}
      |</enum>
      |""".trimMargin()
}
