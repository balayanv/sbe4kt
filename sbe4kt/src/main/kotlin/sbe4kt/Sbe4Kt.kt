package sbe4kt

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import sbe4kt.ir.Schema
import sbe4kt.ir.Struct
import sbe4kt.kt.generateKotlinExtensionsForMessage
import sbe4kt.kt.generateStr64ValueClassFile
import sbe4kt.kt.generateStructFile
import sbe4kt.kt.generateTestsForMessage
import sbe4kt.sbe.generateSbe
import sbe4kt.util.isStruct
import sbe4kt.util.qualifiedName
import java.io.File

internal class KspProvider : SymbolProcessorProvider {
   override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
      KSP.env = environment
      return KSP
   }
}

internal object KSP : SymbolProcessor {
   lateinit var env: SymbolProcessorEnvironment
   internal lateinit var ir: Schema

   internal val log: KSPLogger by lazy { env.logger }
   internal val gen: CodeGenerator by lazy { env.codeGenerator }
   internal val resourcesDir: File by lazy { gen.resourcesDir() }
   internal val sourceSet: String by lazy {
      val path = resourcesDir.path.toString()
      if (path.endsWith("/ksp/test/resources")) "test"
      else if (path.endsWith("/ksp/main/resources")) "main"
      else throw IllegalStateException("failed to detect source set from : [$path]")
   }

   private val generatedStr64Files = mutableSetOf<String>()
   private val generatedStructFiles = mutableSetOf<String>()

   override fun process(resolver: Resolver): List<KSAnnotated> {
      val allTypes = resolver.getSymbolsWithAnnotation("sbe4kt.SBE")
         .filterIsInstance<KSClassDeclaration>()
         .toList()

      val validTypes = allTypes.filter { it.validate() }
      val invalidTypes = allTypes.filter { !it.validate() }

      if (invalidTypes.isNotEmpty()) {
         log.warn("some invalid types found, this may be str64 types that are not yet generated, after second pass there should be none")
      }

      val charSetDefinitions = resolver.getAllFiles()
         .flatMap { it.declarations }
         .filterIsInstance<KSClassDeclaration>()
         .filter { it.classKind == ClassKind.OBJECT }
         .filter { it.superTypes.any { superType -> superType.resolve().declaration.qualifiedName() == "sbe4kt.lib.CharSet" } }
         .toList()

      log.info("pass 1: generate str64 types first")
      if (charSetDefinitions.isNotEmpty()) {
         var generatedAny = false
         charSetDefinitions.forEach { charSet ->
            val qualifiedName = charSet.qualifiedName()
            if (generatedStr64Files.add(qualifiedName)) {
               log.info("pass 1: generating Str64 for $qualifiedName")
               generateStr64ValueClassFile(charSet)
               generatedAny = true
            }
         }

         if (generatedAny && allTypes.isNotEmpty()) {
            log.info("pass 1: deferring ${allTypes.size} type to the round")
            return allTypes
         }
      }

      // TODO this double pass compilation is bit hairy, might need to revisit and simplify later but this works...
      if (validTypes.isEmpty()) return invalidTypes

      log.info("processing ${validTypes.size} @SBE types")
      this.ir = Schema(validTypes)

      generateSbe()

      // Track which composites are used as message fields (they need flyweights)
      val compositesUsedInMessages = ir.messages.flatMap { message ->
         message.fields.mapNotNull { field ->
            (field.type as? Struct)?.name
         }
      }.toSet()

      ir.structs.forEach { struct ->
         val qualifiedName = struct.declaration.qualifiedName()
         if (generatedStructFiles.add(qualifiedName)) {
            val needsFlyweights = !struct.annotation.isStruct() || compositesUsedInMessages.contains(struct.name)
            KSP.generateStructFile(struct, needsFlyweights)
         }
      }
      ir.messages.forEach { message ->
         KSP.generateKotlinExtensionsForMessage(message)
         KSP.generateTestsForMessage(message)
      }
      return invalidTypes
   }

   private fun CodeGenerator.resourcesDir(): File {
      val field = this::class.java.getDeclaredField("resourcesDir")
      field.isAccessible = true
      return field.get(this) as File
   }
}
