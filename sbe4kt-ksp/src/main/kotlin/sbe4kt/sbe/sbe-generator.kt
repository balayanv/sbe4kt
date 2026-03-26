package sbe4kt.sbe

import sbe4kt.KSP
import sbe4kt.ir.*
import sbe4kt.ir.Char
import sbe4kt.ir.Double
import sbe4kt.ir.Enum
import sbe4kt.ir.Float
import sbe4kt.migration.PrevMessage
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate
import java.io.File

internal fun KSP.generateSbe() {
   val evolution = ir.evolution
   val version = evolution?.newVersion ?: 0

   val schemaFile = File(resourcesDir, "sbe-schema.xml")
   val arrayTypes = ir.arraySizes
      .sortedWith(compareBy({ it.first::class.simpleName }, { it.second }))
      .joinToString("\n") { (elementType, length) -> generateArrayType(elementType, length) }
   val structs = ir.structs.joinToString("\n") { generateCompositeType(it) }
   val activeMessages = ir.messages.joinToString("\n") { generateMessageBody(it, version) }
   val deprecatedMessages = ir.deprecatedMessages.joinToString("\n") { generateDeprecatedMessageBody(it, version) }
   val messages = listOf(activeMessages, deprecatedMessages).filter { it.isNotBlank() }.joinToString("\n")

   val xml = """
      |<?xml version='1.0' encoding='UTF-8'?>
      |<sbe:messageSchema xmlns:sbe='http://fixprotocol.io/2016/sbe'
      |                   package='sbe4kt'
      |                   id='1'
      |                   version='$version'
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
      |${arrayTypes.indent(6)}
      |
      |${structs.indent(6)}
      |   </types>
      |
      |${messages.indent(3)}
      |
      |</sbe:messageSchema>
      |""".trimTemplate()

   schemaFile.parentFile?.mkdirs()
   schemaFile.writeText(xml)

   // copy to src resources as current-sbe-schema.xml
   copyToSourceResources(schemaFile)
}

private fun KSP.copyToSourceResources(schemaFile: File) {
   val projectRoot = resourcesDir
      .parentFile  // main|test
      .parentFile  // ksp
      .parentFile  // generated
      .parentFile  // build
      .parentFile  // project root

   val targetDir = File(projectRoot, "src/$sourceSet/resources")
   targetDir.mkdirs()
   val targetFile = File(targetDir, "current-sbe-schema.xml")
   schemaFile.copyTo(targetFile, overwrite = true)
   log.info("copied schema to ${targetFile.path}")
}

private fun generateMessageBody(message: Message, schemaVersion: Int): String {
   val evolution = message.previousMessage?.let { _ ->
      // build set of new field names for this message
      val prevFieldNames = message.previousMessage.fields.map { it.name }.toSet()
      val currentFieldNames = message.fields.map { it.name }.toSet()
      currentFieldNames - prevFieldNames
   } ?: emptySet()

   data class SbeFieldEntry(
      val name: String,
      val id: Int,
      val type: String,
      val offset: Int?,
      val sbeOrder: Int,
      val sinceVersion: Int?,
      val deprecated: String?,
      val description: String?,
      val presence: String?,
      val usePadding: Boolean,
   )

   // current fields
   val currentEntries = message.fields.map { field ->
      val sinceVersion = when {
         field.name in evolution -> schemaVersion
         message.previousMessage != null -> {
            val prevField = message.previousMessage.fieldsByName[field.name]
            prevField?.sinceVersion
         }
         else -> null
      }

      val prevField = message.previousMessage?.fieldsByName?.get(field.name)
      val hadDeprecation = prevField?.deprecated != null
      val userClearedDeprecation = hadDeprecation && field.removeAfter <= 0

      val deprecated = when {
         field.removeAfter > 0 -> "${field.removeAfter}"
         userClearedDeprecation -> null
         else -> prevField?.deprecated
      }

      val description = when {
         field.removeAfter > 0 -> "scheduled for removal at schema version ${field.removeAfter}"
         userClearedDeprecation -> "deprecation cleared since version $schemaVersion, field is no longer scheduled for removal"
         else -> null
      }

      SbeFieldEntry(
         name = field.name,
         id = field.id,
         type = field.type.sbeName,
         offset = field.offset,
         sbeOrder = field.sbeOrder,
         sinceVersion = sinceVersion,
         deprecated = deprecated,
         description = description,
         presence = if (field.isNullable) "optional" else null,
         usePadding = message.usePadding,
      )
   }

   // deprecated fields (removed from class but kept in schema for wire compatibility)
   val deprecatedEntries = message.deprecatedFields.map { prevField ->
      val fieldName = if (prevField.name.endsWith("Removed")) prevField.name else "${prevField.name}Removed"
      val depVersion = prevField.deprecated ?: "$schemaVersion"
      SbeFieldEntry(
         name = fieldName,
         id = prevField.id,
         type = prevField.type,
         offset = prevField.offset,
         sbeOrder = prevField.sbeOrder,
         sinceVersion = prevField.sinceVersion,
         deprecated = depVersion,
         description = "removed from source since version $depVersion, space reserved for wire compatibility",
         presence = prevField.presence,
         usePadding = message.usePadding,
      )
   }

   val allFields = (currentEntries + deprecatedEntries).sortedBy { it.sbeOrder }

   val xml = allFields.joinToString("\n") { entry ->
      val offsetAttr = if (entry.usePadding && entry.offset != null && entry.offset >= 0) " offset='${entry.offset}'" else ""
      val presenceAttr = if (entry.presence != null) " presence='${entry.presence}'" else ""
      val sinceAttr = if (entry.sinceVersion != null && entry.sinceVersion > 0) " sinceVersion='${entry.sinceVersion}'" else ""
      val deprecatedAttr = if (entry.deprecated != null) " deprecated='${entry.deprecated}'" else ""
      val descAttr = if (entry.description != null) " description='${entry.description}'" else ""
      "<field name='${entry.name}' id='${entry.id}' type='${entry.type}'$offsetAttr$presenceAttr$sinceAttr$deprecatedAttr$descAttr/>"
   }

   val blockLengthAttribute = if (message.usePadding && message.blockLength > 0) {
      " blockLength='${message.blockLength}'"
   } else if (message.deprecatedFields.isNotEmpty()) {
      // need explicit blockLength when deprecated fields occupy space
      " blockLength='${message.blockLength}'"
   } else {
      ""
   }

   return """
      |<sbe:message name='${message.name}' id='${message.id}'$blockLengthAttribute description='sbe4kt generated sbe schema for ${message.name}'>
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
      is ArrayN, is StringN, is Enum, is Struct, is Union -> false
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

private fun generateDeprecatedMessageBody(prevMsg: PrevMessage, schemaVersion: Int): String {
   val deprecatedVersion = if (prevMsg.isDeprecated) prevMsg.deprecated else schemaVersion
   return "<sbe:message name='${prevMsg.name}' id='${prevMsg.id}' deprecated='$deprecatedVersion' description='sbe4kt generated sbe schema for ${prevMsg.name} was removed in since version $deprecatedVersion'/>"
}

private fun generateArrayType(elementType: IR.Leaf, length: Int): String {
   val (typeName, primitiveType, charEnc) = when (elementType) {
      is Int8 -> Triple("ByteArray$length", "uint8", "")
      is Int16 -> Triple("ShortArray$length", "int16", "")
      is Int32 -> Triple("IntArray$length", "int32", "")
      is Int64 -> Triple("LongArray$length", "int64", "")
      is Float -> Triple("FloatArray$length", "float", "")
      is Double -> Triple("DoubleArray$length", "double", "")
      is Char -> Triple("CharArray$length", "char", " characterEncoding='US-ASCII'")
      else -> error("Unsupported array element type for SBE schema: ${elementType::class.simpleName}")
   }
   return "<type name='$typeName' primitiveType='$primitiveType' length='$length'$charEnc/>"
}
