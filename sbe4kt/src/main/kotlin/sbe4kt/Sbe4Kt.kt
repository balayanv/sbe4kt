package sbe4kt

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.validate
import sbe4kt.ir.IrSchema
import sbe4kt.ir.buildIr
import sbe4kt.kt.generateKotlinExtensions
import sbe4kt.sbe.generateSbe

internal class KspProvider : SymbolProcessorProvider {
   override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
      KSP.env = environment
      return KSP
   }
}

internal object KSP : SymbolProcessor {
   lateinit var env: SymbolProcessorEnvironment
   lateinit var ir: IrSchema

   val log: KSPLogger by lazy { env.logger }
   val gen: CodeGenerator by lazy { env.codeGenerator }

   override fun process(resolver: Resolver): List<KSAnnotated> {
      ir = buildIr(resolver)
      generateSbe()
      generateKotlinExtensions()
      return emptyList()
   }
}
