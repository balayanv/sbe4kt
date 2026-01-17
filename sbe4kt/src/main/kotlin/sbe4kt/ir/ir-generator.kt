package sbe4kt.ir

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import sbe4kt.KSP.log
import sbe4kt.util.requireMutableDataClass


internal fun buildIr(resolver: Resolver): IrSchema {
   val ir = IrSchema()

   val sourcePaths = HashSet<String>()

   resolver.getSymbolsWithAnnotation("sbe4kt.SBE")
      .filter { it is KSClassDeclaration && it.validate()  }
      .map { it as KSClassDeclaration }
      .sortedBy { it.qualifiedName?.asString() } // Sort for deterministic order
      .forEach { type ->
         type.containingFile?.filePath?.let {
            sourcePaths.add(it)
         }
         type.requireMutableDataClass()
         ir.add(IrMessage(type))
      }

   if(sourcePaths.all { it.contains("/src/test/") }){
      ir.sourceSet = "test"
   }else if(sourcePaths.all { it.contains("/src/main/") }){
      ir.sourceSet = "main"
   }else{
      log.error("detected source files both from test and main sets : $sourcePaths")
      throw IllegalStateException()
   }

   ir.prepare()
   return ir
}

