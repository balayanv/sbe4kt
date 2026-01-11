package com.example.sbe4kt

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate
import java.io.File

class SbeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("com.example.sbe4kt.SBE")
        val ret = symbols.filter { !it.validate() }.toList()
        
        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(SbeVisitor(logger), Unit) }
            
        return ret
    }

    inner class SbeVisitor(private val logger: KSPLogger) : com.google.devtools.ksp.symbol.KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            if (!classDeclaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.DATA)) {
                logger.error("Classes annotated with @SBE must be data classes", classDeclaration)
                return
            }

            val properties = classDeclaration.getAllProperties().toList()
            val allMutable = properties.all { it.isMutable }
            if (!allMutable) {
                logger.error("All properties in @SBE data class must be mutable (var)", classDeclaration)
                return
            }

            generateSbe(classDeclaration, properties)
        }

        private fun generateSbe(classDeclaration: KSClassDeclaration, properties: List<KSPropertyDeclaration>) {
            val packageName = classDeclaration.packageName.asString().ifEmpty { "com.example.sbe.generated" }
            val className = classDeclaration.simpleName.asString()
            val schemaName = "${className}Schema"
            
            // Build SBE XML
            val xml = buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                appendLine("<sbe:messageSchema xmlns:sbe=\"http://fixprotocol.io/2016/sbe\"")
                appendLine("    package=\"$packageName\"")
                appendLine("    id=\"1\"")
                appendLine("    version=\"0\"")
                appendLine("    semanticVersion=\"5.2\"")
                appendLine("    description=\"$className SBE Schema\"")
                appendLine("    byteOrder=\"littleEndian\">")
                appendLine("    <types>")
                appendLine("        <composite name=\"messageHeader\" description=\"Message identifiers and length of message root\">")
                appendLine("            <type name=\"blockLength\" primitiveType=\"uint16\"/>")
                appendLine("            <type name=\"templateId\"  primitiveType=\"uint16\"/>")
                appendLine("            <type name=\"schemaId\"    primitiveType=\"uint16\"/>")
                appendLine("            <type name=\"version\"     primitiveType=\"uint16\"/>")
                appendLine("        </composite>")
                appendLine("    </types>")
                appendLine("    <sbe:message name=\"$className\" id=\"1\" description=\"$className\">")
                
                properties.forEachIndexed { index, prop ->
                    val type = mapKotlinTypeToSbeType(prop)
                    if (type != null) {
                        appendLine("        <field name=\"${prop.simpleName.asString()}\" id=\"${index + 1}\" type=\"$type\"/>")
                    } else {
                        logger.warn("Skipping property ${prop.simpleName.asString()} due to unsupported type", prop)
                    }
                }
                
                appendLine("    </sbe:message>")
                appendLine("</sbe:messageSchema>")
            }

            // Write XML to temp file
            val tempFile = File.createTempFile("sbe-schema-", ".xml")
            tempFile.writeText(xml)
            
            // Invoke SBE Tool
            try {
                // Determine output directory - KSP doesn't easily expose a "safe" generated source root for external tools 
                // that write to disk themselves. We usually need to trick it or just use the project build directory.
                // However, KSP's codeGenerator is for writing files directly. 
                // Since SBE Tool handles writing, we might need to point it to a location that is included in compilation.
                // Use the configured output directory or fallback
                val outputDirPath = options["sbeOutputDir"] ?: "build/generated/sbe"
                val outputDir = File(outputDirPath)
                outputDir.mkdirs()
                
                System.setProperty("sbe.output.dir", outputDir.absolutePath)
                System.setProperty("sbe.target.language", "Java") // Aeron SBE generates Java
                uk.co.real_logic.sbe.SbeTool.main(arrayOf(tempFile.absolutePath))
                
                logger.info("Generated SBE codecs for $className to ${outputDir.absolutePath}")
                
            } catch (e: Exception) {
                logger.error("Failed to run SBE Tool: ${e.message}", classDeclaration)
            } finally {
                tempFile.delete()
            }
        }

        private fun mapKotlinTypeToSbeType(prop: KSPropertyDeclaration): String? {
            val typeName = prop.type.resolve().declaration.qualifiedName?.asString()
            return when (typeName) {
                "kotlin.Int" -> "int32"
                "kotlin.Long" -> "int64"
                "kotlin.Short" -> "int16"
                "kotlin.Byte" -> "int8"
                "kotlin.Double" -> "double"
                "kotlin.Float" -> "float"
                "kotlin.Char" -> "char"
                "kotlin.Boolean" -> "uint8" // Approximation
                else -> null
            }
        }
    }
}
