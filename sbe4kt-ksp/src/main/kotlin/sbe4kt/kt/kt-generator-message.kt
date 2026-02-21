package sbe4kt.kt

import com.google.devtools.ksp.processing.Dependencies
import sbe4kt.KSP
import sbe4kt.ir.Message
import sbe4kt.util.trimTemplate

internal fun KSP.generateKotlinExtensionsForMessage(
   message: Message
) {
   val packageName = message.packageStr
   val className = message.name
   val fileName = "${className}Extensions"

   val file = gen.createNewFile(
      Dependencies(true, *message.sourceFiles()),
      packageName,
      fileName
   )

   // Generate imports section
   val importsSection = message.allImports().joinToString("\n") { "import $it" }

   // Generate the different sections
   val helpersSection = generateEncodersSection(message)
   val schemaVersion = ir.evolution?.newVersion ?: 0
   val codecSection = generateCodecSection(message, schemaVersion, schemaId = 1)
   val flyweightsSection = generateFlyweightsSection(message)
   val utilsSection = generateUtilsSection(message)
   val microTagSection = generateMicroTagSection(message)

   val result = """
      |package $packageName
      |
      |$importsSection
      |$helpersSection
      |
      |$codecSection
      |
      |$flyweightsSection
      |
      |$utilsSection
      |
      |$microTagSection
      |""".trimTemplate()

   file.bufferedWriter().use { it.write(result) }
}
