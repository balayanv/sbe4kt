package sbe4kt.sbe

import sbe4kt.KSP.ir
import sbe4kt.KSP.log
import uk.co.real_logic.sbe.SbeTool
import java.io.File

internal fun generateSbe() {
   if (ir.types.isEmpty()) {
      return
   }

   val firstMessage = ir.types.first()
   // Infer project root from the first source file
   val firstSourceFile = File(firstMessage.type.containingFile!!.filePath)
   // Walk up until we find 'src' directory, then the parent of that is likely the module root
   var projectRoot = firstSourceFile.parentFile
   while (projectRoot != null && !File(projectRoot, "src").exists() && !File(projectRoot, "build.gradle.kts").exists()) {
      projectRoot = projectRoot.parentFile
   }
   
   if (projectRoot == null) {
      log.warn("Could not determine project root. Defaulting to working directory.")
      projectRoot = File(".")
   }

   val sourceSet = ir.sourceSet
   val schemaFile = File(projectRoot, "src/$sourceSet/resources/sbe-schema.xml")

   log.info("Generating consolidated SBE schema to $schemaFile")


   val messageBodies = ir.types.joinToString("\n") { message ->
      generateMessageBody(message)
   }

   // Build SBE XML
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
    </types>
$messageBodies
</sbe:messageSchema>
   """.trimIndent()

   // Write XML to persistent file
   // Ensure parent directory exists
   schemaFile.parentFile?.mkdirs()
   schemaFile.writeText(xml)
   
   val outputDir = File(projectRoot, "build/generated/sbe/$sourceSet/java")
   // Invoke SBE Tool
   try {
      System.setProperty("sbe.output.dir", outputDir.absolutePath)
      System.setProperty("sbe.target.language", "Java")

      // Only generate package-info.java for main output to avoid duplicates
      val shouldGeneratePackageInfo = sourceSet == "main"
      System.setProperty("sbe.java.generate.package.info", shouldGeneratePackageInfo.toString())

      SbeTool.main(arrayOf(schemaFile.absolutePath))

      if (!shouldGeneratePackageInfo) {
         outputDir.walk().filter { it.name == "package-info.java" }.forEach { it.delete() }
      }

      log.info("Generated SBE codecs to ${outputDir.absolutePath} (addPackageInfo=$shouldGeneratePackageInfo)")

   } catch (e: Exception) {
      log.error("Failed to run SBE Tool: ${e.message}", firstMessage.type.containingFile)
   }
}

private fun generateMessageBody(message: sbe4kt.ir.IrType): String {
   var currentOffset = 0
   val fieldsXml = message.fields.joinToString("\n") { field ->
      var offsetAttribute = ""
      if (message.usePadding) {
         // Align currentOffset to next multiple of 8
         val padding = (8 - (currentOffset % 8)) % 8
         val alignedOffset = currentOffset + padding
         offsetAttribute = """ offset="$alignedOffset""""
         currentOffset = alignedOffset + field.type.size
      }

      """        <field name="${field.name}" id="${field.id}" type="${field.type.sbeName}"$offsetAttribute/>"""
   }

   return """
    <sbe:message name="${message.className}" id="${message.id}" description="${message.className}">
$fieldsXml
    </sbe:message>
   """
}
