package sbe4kt.ir

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import sbe4kt.KSP.log


internal fun buildIr(resolver: Resolver): IrContext {
   val ir = IrContext()

   val sourcePaths = HashSet<String>()

   resolver.getSymbolsWithAnnotation("sbe4kt.SBE")
      .filter { it is KSClassDeclaration && it.validate() }
      .map { it as KSClassDeclaration }
      .sortedBy { it.qualifiedName?.asString() } // Sort for deterministic order
      .forEach { type ->
         type.containingFile?.filePath?.let {
            sourcePaths.add(it)
         }
         validateMutableDataClass(type)
         ir.add(IrType(type))
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

private fun validateMutableDataClass(type: KSClassDeclaration) {
   log.info("Validating ${type.containingFile?.filePath}")

   if (!type.modifiers.contains(Modifier.DATA)) {
      log.error("Classes annotated with @SBE must be data classes", type)
      throw IllegalStateException()
   }

   type.getAllProperties().forEach { prop ->
      if (!prop.isMutable) {
         log.error("${type.simpleName.asString()}.${prop.simpleName.asString()} is not mutable", prop)
         throw IllegalStateException()
      }
   }
}
