package sbe4kt

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import sbe4kt.ir.DeferredTypeException
import sbe4kt.ir.Schema
import sbe4kt.ir.Struct
import sbe4kt.kt.generateKotlinExtensionsForMessage
import sbe4kt.kt.generateStr64ValueClassFile
import sbe4kt.kt.generateStructFile
import sbe4kt.kt.generateTestsForMessage
import sbe4kt.migration.SbeSchemaParser
import sbe4kt.sbe.generateSbe
import sbe4kt.util.isSealed
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

   internal val projectRoot: File by lazy {
      resourcesDir
         .parentFile  // main|test
         .parentFile  // ksp
         .parentFile  // generated
         .parentFile  // build
         .parentFile  // project root
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

      // Separate types that reference unresolved sealed interfaces (need another KSP round)
      val readyTypes = mutableListOf<KSClassDeclaration>()
      val deferredTypes = mutableListOf<KSClassDeclaration>()
      for (type in validTypes) {
         try {
            type.getAllProperties().forEach { prop ->
               val resolved = prop.type.resolve().declaration
               if (resolved is KSClassDeclaration && resolved.isSealed() && resolved.getSealedSubclasses().toList().isEmpty()) {
                  throw DeferredTypeException("${type.simpleName.asString()} uses sealed type with unresolved subclasses")
               }
            }
            readyTypes.add(type)
         } catch (e: DeferredTypeException) {
            log.warn("deferring: ${e.message}")
            deferredTypes.add(type)
         }
      }

      if (readyTypes.isEmpty()) return invalidTypes + deferredTypes

      log.info("processing ${readyTypes.size} @SBE types")

      val lastSchemaFile = File(projectRoot, "src/$sourceSet/resources/last-sbe-schema.xml")
      val previousSchema = SbeSchemaParser.parse(lastSchemaFile)
      if (previousSchema != null) {
         log.info("loaded previous schema (version ${previousSchema.version}) with ${previousSchema.messages.size} messages")
      }

      this.ir = Schema(readyTypes, previousSchema)

      val evolution = ir.evolution
      if (evolution != null) {
         for (warning in evolution.warnings) log.warn("schema evolution: $warning")
         for (breaking in evolution.breakingChanges) {
            log.error("BREAKING CHANGE: ${breaking.message}")
         }
         if (evolution.hasChanges) {
            log.info("schema evolved: version ${previousSchema!!.version} -> ${evolution.newVersion}")
         }
      }

      generateSbe()

      // Track which composites are used as message fields (they need flyweights)
      val compositesUsedInMessages = ir.messages.flatMap { message ->
         message.fields.mapNotNull { field ->
            (field.type as? Struct)?.name
               ?: (field.type as? sbe4kt.ir.ArrayN)?.takeIf { it.isStruct }?.structType?.name
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
      return invalidTypes + deferredTypes
   }

   private fun CodeGenerator.resourcesDir(): File {
      val field = this::class.java.getDeclaredField("resourcesDir")
      field.isAccessible = true
      return field.get(this) as File
   }
}
