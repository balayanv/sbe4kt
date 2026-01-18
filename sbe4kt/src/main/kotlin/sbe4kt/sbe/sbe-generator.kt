package sbe4kt.sbe

import sbe4kt.KSP.ir
import sbe4kt.KSP.log
import java.io.File

internal fun generateSbe() {
   if (ir.types.isEmpty()) {
      return
   }

   val firstMessage = ir.types.first()
   val firstSourceFile = File(firstMessage.type.containingFile!!.filePath)
   var projectRoot = firstSourceFile.parentFile
   while (projectRoot != null && !File(projectRoot, "src").exists() && !File(projectRoot, "build.gradle.kts").exists()) {
      projectRoot = projectRoot.parentFile
   }

   if (projectRoot == null) {
      throw IllegalStateException("Could not determine project root. Defaulting to working directory.")
   }

   val sourceSet = ir.sourceSet
   val schemaFile = File(projectRoot, "build/generated/ksp/$sourceSet/resources/sbe-schema.xml")

   log.info("Generating SBE schema to $schemaFile")

   val compositeTypes = mutableMapOf<String, sbe4kt.ir.IR.Composite>()
   val charSeqSizes = mutableSetOf<Int>()

   ir.types.forEach { message ->
      message.fields.forEach { field ->
         if (field.type is sbe4kt.ir.IR.Composite) {
            compositeTypes[field.type.sbeName] = field.type
         }
         if (field.type is sbe4kt.ir.IR.FixedCharSeq) {
            charSeqSizes.add(field.type.fixedSize)
         }
      }
   }

   // Generate FixedCharSeq type definitions
   val charSeqTypesXml = charSeqSizes.sorted().joinToString("\n") { size ->
      """        <type name="CharSeq$size" primitiveType="char" length="$size" characterEncoding="US-ASCII"/>"""
   }

   // Generate composite type definitions
   val compositeTypesXml = if (compositeTypes.isNotEmpty()) {
      compositeTypes.values.joinToString("\n") { composite ->
         generateCompositeType(composite)
      }
   } else {
      ""
   }

   val messageBodies = ir.types.joinToString("\n") { message ->
      generateMessageBody(message)
   }

   val xml = """
<?xml version="1.0" encoding="UTF-8"?>
<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe"
    package="sbe4kt"
    id="1"
    version="0"
    semanticVersion="5.2"
    description="Consolidated SBE Schema"
    byteOrder="littleEndian">
    <types>
        <composite name="messageHeader" description="Message identifiers and length of message root">
            <type name="blockLength" primitiveType="uint16"/>
            <type name="templateId"  primitiveType="uint16"/>
            <type name="schemaId"    primitiveType="uint16"/>
            <type name="version"     primitiveType="uint16"/>
        </composite>
        <enum name="Bool" encodingType="uint8">
            <validValue name="F">0</validValue>
            <validValue name="T">1</validValue>
        </enum>
$charSeqTypesXml
$compositeTypesXml
    </types>
$messageBodies
</sbe:messageSchema>
   """.trimIndent()

   // Write XML schema to KSP output directory (not src directory)
   // Gradle tasks will handle SBE code generation from this schema
   schemaFile.parentFile?.mkdirs()
   schemaFile.writeText(xml)

   log.info("SBE schema written to ${schemaFile.absolutePath}")
   log.info("SBE Java generation will be handled by Gradle task")
}

private fun generateMessageBody(message: sbe4kt.ir.IrMessage): String {
   // Sort fields by their SBE order (computed in prepare())
   val sortedFields = message.fields.sortedBy { it.sbeOrder }

   val fieldsXml = sortedFields.joinToString("\n") { field ->
      // Only add offset attribute when usePadding is true and offset has been computed
      val offsetAttribute = if (message.usePadding && field.offset >= 0) {
         """ offset="${field.offset}""""
      } else {
         ""
      }

      """        <field name="${field.name}" id="${field.id}" type="${field.type.sbeName}"$offsetAttribute/>"""
   }

   // Only add blockLength attribute when usePadding is true and blockLength has been computed
   val blockLengthAttribute = if (message.usePadding && message.blockLength > 0) {
      """ blockLength="${message.blockLength}""""
   } else {
      ""
   }

   return """
    <sbe:message name="${message.className}" id="${message.id}"$blockLengthAttribute description="${message.className}">
$fieldsXml
    </sbe:message>
   """
}

private fun generateCompositeType(composite: sbe4kt.ir.IR.Composite): String {
   val fieldsXml = composite.fields.joinToString("\n") { field ->
      """            <type name="${field.name}" primitiveType="${field.type.sbeName}"/>"""
   }

   return """        <composite name="${composite.sbeName}">
$fieldsXml
        </composite>"""
}
