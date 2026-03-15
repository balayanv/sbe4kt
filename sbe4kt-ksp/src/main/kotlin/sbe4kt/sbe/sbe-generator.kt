package sbe4kt.sbe

import sbe4kt.KSP
import sbe4kt.ir.ByteArrayN
import sbe4kt.ir.Enum
import sbe4kt.ir.IR
import sbe4kt.ir.RepeatingGroup
import sbe4kt.ir.Message
import sbe4kt.ir.StringN
import sbe4kt.ir.Struct
import sbe4kt.ir.Union
import sbe4kt.ir.VarStringN
import sbe4kt.META
import sbe4kt.migration.PrevMessage
import sbe4kt.util.indent
import sbe4kt.util.trimTemplate
import java.io.File

internal fun KSP.generateSbe() {
   val evolution = ir.evolution
   val version = evolution?.newVersion ?: 0

   val schemaFile = File(resourcesDir, "sbe-schema.xml")
   val byteArrayTypes = ir.byteArraySizes.joinToString("\n") { generateByteArrayType(it) }
   val stringTypes = ir.fixedStrings.joinToString("\n") { generateStringType(it) }

   // Variable-length string encoding composites
   val usedVarEncodings = ir.messages.flatMap { it.fields.map { f -> f.type } }
      .filterIsInstance<VarStringN>()
      .map { it.encoding }
      .toSet()
   val varStringComposites = usedVarEncodings.joinToString("\n") { encoding ->
      val charEnc = if (encoding == META.Encoding.UTF8) "UTF-8" else "US-ASCII"
      val name = if (encoding == META.Encoding.UTF8) "varStringEncoding" else "varAsciiEncoding"
      "<composite name='$name'>\n" +
      "   <type name='length' primitiveType='uint32' maxValue='1073741824'/>\n" +
      "   <type name='varData' primitiveType='uint8' length='0' characterEncoding='$charEnc'/>\n" +
      "</composite>"
   }

   val hasGroups = ir.messages.any { msg -> msg.fields.any { it.type is RepeatingGroup } }
   val groupSizeComposite = if (hasGroups) {
      "<composite name='groupSizeEncoding'>\n" +
      "   <type name='blockLength' primitiveType='uint16'/>\n" +
      "   <type name='numInGroup' primitiveType='uint16'/>\n" +
      "</composite>"
   } else ""

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
      |${byteArrayTypes.indent(6)}
      |
      |${stringTypes.indent(6)}
      |
      |${varStringComposites.indent(6)}
      |
      |${groupSizeComposite.indent(6)}
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

   // current fields (excluding group fields, handled separately)
   val currentEntries = message.fields.filter { it.type !is RepeatingGroup }.map { field ->
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

   val allEntries = (currentEntries + deprecatedEntries).sortedBy { it.sbeOrder }
   val fixedEntries = allEntries.filter { it.offset == null || it.offset >= 0 }
   val varDataEntries = allEntries.filter { it.offset != null && it.offset < 0 }

   val fixedXml = fixedEntries.joinToString("\n") { entry ->
      val offsetAttr = if (entry.usePadding && entry.offset != null && entry.offset >= 0) " offset='${entry.offset}'" else ""
      val presenceAttr = if (entry.presence != null) " presence='${entry.presence}'" else ""
      val sinceAttr = if (entry.sinceVersion != null && entry.sinceVersion > 0) " sinceVersion='${entry.sinceVersion}'" else ""
      val deprecatedAttr = if (entry.deprecated != null) " deprecated='${entry.deprecated}'" else ""
      val descAttr = if (entry.description != null) " description='${entry.description}'" else ""
      "<field name='${entry.name}' id='${entry.id}' type='${entry.type}'$offsetAttr$presenceAttr$sinceAttr$deprecatedAttr$descAttr/>"
   }

   val varDataXml = varDataEntries.joinToString("\n") { entry ->
      "<data name='${entry.name}' id='${entry.id}' type='${entry.type}'/>"
   }

   // Group fields → <group> elements (after <field>, before <data>)
   val groupFields = message.fields.filter { it.type is RepeatingGroup }.sortedBy { it.sbeOrder }
   val groupXml = groupFields.joinToString("\n") { field ->
      val group = field.type as RepeatingGroup
      val struct = group.elementType as Struct
      val innerFields = struct.fields.sortedBy { it.sbeOrder }.joinToString("\n") { f ->
         "   <field name='${f.name}' id='${f.id}' type='${f.type.sbeName}'/>"
      }
      "<group name='${field.name}' id='${field.id}' dimensionType='groupSizeEncoding'>\n$innerFields\n</group>"
   }

   val xml = listOf(fixedXml, groupXml, varDataXml).filter { it.isNotBlank() }.joinToString("\n")

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

private fun generateDeprecatedMessageBody(prevMsg: PrevMessage, schemaVersion: Int): String {
   val deprecatedVersion = if (prevMsg.isDeprecated) prevMsg.deprecated else schemaVersion
   return "<sbe:message name='${prevMsg.name}' id='${prevMsg.id}' deprecated='$deprecatedVersion' description='sbe4kt generated sbe schema for ${prevMsg.name} was removed in since version $deprecatedVersion'/>"
}

private fun generateByteArrayType(size: Int) = "<type name='ByteArray$size' primitiveType='uint8' length='$size'/>"

private fun generateStringType(strN: StringN) = "<type name='${strN.sbeName}' primitiveType='char' length='${strN.length}' characterEncoding='${strN.encoding.sbeEncoding}'/>"
