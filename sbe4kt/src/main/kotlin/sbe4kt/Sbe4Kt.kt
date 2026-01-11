package sbe4kt

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.validate
import sbe4kt.ir.IrContext
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
   lateinit var ir: IrContext

   val log: KSPLogger by lazy { env.logger }
   val gen: CodeGenerator by lazy { env.codeGenerator }

   override fun process(resolver: Resolver): List<KSAnnotated> {
      val symbols = resolver.getSymbolsWithAnnotation("sbe4kt.SBE")
      val ret = symbols.filter { !it.validate() }.toList()
      ir = buildIr(resolver)
      generateSbe()
      generateKotlinExtensions()

      return ret
   }
}
