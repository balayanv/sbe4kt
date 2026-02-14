package sbe4kt.ir

import com.google.devtools.ksp.symbol.KSClassDeclaration
import sbe4kt.util.importName
import sbe4kt.util.packageName
import sbe4kt.util.simpleName

internal data class Enum(
   val declaration: KSClassDeclaration,
) : IR.Leaf {
   override val name: String = declaration.simpleName()
   override val sbeName = "int8"

   override val packageStr = declaration.packageName()

   override val defaultValue = "defaultValue<$name>()"
   override val randomValue = "randomValue<$name>()"
   override val size = 1

   override val imports by lazy {
      buildSet {
         // Library functions for default/random values
         add("sbe4kt.lib.defaultValue")
         add("sbe4kt.lib.randomValue")

         add(declaration.importName())
      }
   }

   override fun convertToSbe(value: String) = "$value.ordinal.toUByte().toByte()"
   override fun convertFromSbe(value: String) = "$name.entries[$value.toUByte().toInt()]"
}