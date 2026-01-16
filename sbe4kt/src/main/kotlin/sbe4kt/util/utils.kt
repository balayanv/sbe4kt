package sbe4kt.util

import com.google.devtools.ksp.symbol.ClassKind.ENUM_CLASS
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Modifier.INLINE
import com.google.devtools.ksp.symbol.Modifier.VALUE
import sbe4kt.KSP.log

internal fun KSDeclaration.qualifiedName() = qualifiedName!!.asString()
internal fun KSDeclaration.simpleName() = simpleName.asString()

internal fun KSClassDeclaration.isValueClass() = modifiers.contains(INLINE) || modifiers.contains(VALUE)
internal fun KSClassDeclaration.isEnum() = classKind == ENUM_CLASS

internal fun KSClassDeclaration.packageName(): String = if (parentDeclaration is KSClassDeclaration) {
   val parent = parentDeclaration as KSClassDeclaration
   "${packageName.asString()}.${parent.simpleName()}"
} else {
   packageName.asString()
}


internal fun KSClassDeclaration.requireMutableDataClass() {
   log.info("Validating ${this.containingFile?.filePath}")

   if (!this.modifiers.contains(Modifier.DATA)) {
      log.error("must be mutable data classes", this)
      throw IllegalStateException()
   }

   this.getAllProperties().forEach { prop ->
      if (!prop.isMutable) {
         log.error("${this.simpleName.asString()}.${prop.simpleName.asString()} is not mutable", prop)
         throw IllegalStateException()
      }
   }
}

internal fun KSPropertyDeclaration.isComposite() = annotations.find { it.shortName.asString() == "Metadata" }?.arguments?.find { it.name?.asString() == "isComposite" }?.value as? Boolean ?: false