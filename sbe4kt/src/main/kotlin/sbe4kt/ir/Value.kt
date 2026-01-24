package sbe4kt.ir

import com.google.devtools.ksp.symbol.KSClassDeclaration
import sbe4kt.util.packageName
import sbe4kt.util.simpleName

internal data class Value(
   val declaration: KSClassDeclaration,
   val underlyingType: IR.Leaf,
) : IR.Leaf {
   override val sbeName = underlyingType.sbeName
   override val name = declaration.simpleName()
   override val defaultValue = "${name}(${underlyingType.defaultValue})"
   override val randomValue = "${name}(${underlyingType.randomValue})"
   override val size = underlyingType.size
   override val packageStr = declaration.packageName()
   val innerName = declaration.primaryConstructor!!.parameters.first().name!!.getShortName()

   override val imports by lazy {
      buildSet {
         // Add imports from the underlying type
         addAll(underlyingType.imports)

         // The value class itself
         val fullName = if (packageStr.isNotEmpty()) "$packageStr.$name" else name
         add(fullName)
      }
   }

   override fun convertToSbe(value: String) = underlyingType.convertToSbe("$value.$innerName")
   override fun convertFromSbe(value: String) = "$name(${underlyingType.convertFromSbe(value)})"
}