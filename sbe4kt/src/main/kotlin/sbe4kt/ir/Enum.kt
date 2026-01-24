package sbe4kt.ir

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import sbe4kt.util.packageName
import sbe4kt.util.simpleName

internal data class Enum(
   val declaration: KSClassDeclaration,
) : IR.Leaf {
   override val name: String = declaration.simpleName()
   override val sbeName = name // Use the enum's actual name as the SBE type

   override val packageStr = declaration.packageName()

   override val defaultValue = "defaultValue<$name>()"
   override val randomValue = "randomValue<$name>()"
   override val size = 1

   override val imports by lazy {
      buildSet {
         // Library functions for default/random values
         add("sbe4kt.lib.defaultValue")
         add("sbe4kt.lib.randomValue")

         // The Kotlin enum type itself (not the SBE generated one)
         val fullName = if (packageStr.isNotEmpty()) "$packageStr.$name" else name
         add(fullName)

         // Note: SBE generated enum (sbe4kt.$sbeName) is used internally in conversion
         // but should not be imported in the extension file - it's fully qualified in use
      }
   }

   override fun convertToSbe(value: String) = "sbe4kt.$sbeName.entries[$value.ordinal]"
   override fun convertFromSbe(value: String) = "$name.entries[$value.ordinal]"

   val entries: List<String> by lazy {
      declaration.declarations
         .filterIsInstance<KSClassDeclaration>()
         .filter { it.classKind == ClassKind.ENUM_ENTRY }
         .map { it.simpleName.asString() }
         .toList()
   }
}