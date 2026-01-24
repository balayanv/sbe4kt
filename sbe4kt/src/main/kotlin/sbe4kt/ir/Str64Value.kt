package sbe4kt.ir

import com.google.devtools.ksp.symbol.KSClassDeclaration
import sbe4kt.util.packageName
import sbe4kt.util.simpleName

/**
 * Represents a Str64 value class.
 * These are value classes that wrap a Long and implement CharSequence.
 */
internal data class Str64Value(
   val declaration: KSClassDeclaration,
) : IR.Leaf {
   override val sbeName = "int64"
   override val name = declaration.simpleName()
   override val defaultValue = "${name}(0L)"
   override val randomValue = "${name}.randomStr()"
   override val size = 8
   override val packageStr = declaration.packageName()

   override val imports by lazy {
      buildSet {
         val fullName = if (packageStr.isNotEmpty()) "$packageStr.$name" else name
         add(fullName)
      }
   }

   override fun convertToSbe(value: String) = "$value.value"
   override fun convertFromSbe(value: String) = "$name($value)"
}
