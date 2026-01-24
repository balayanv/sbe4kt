package sbe4kt.ir

import com.google.devtools.ksp.symbol.KSClassDeclaration
import sbe4kt.util.getFieldIRs
import sbe4kt.util.packageName
import sbe4kt.util.sbeAnnotation
import sbe4kt.util.simpleName
import sbe4kt.util.usePadding
import kotlin.Boolean

internal typealias Composite = Struct

internal data class Struct(
   val declaration: KSClassDeclaration,
) : IR.Leaf, IR.Root {
   val annotation = declaration.sbeAnnotation()
   val fields = declaration.getFieldIRs()
   val usePadding: Boolean = annotation.usePadding()
   var blockLength: Int = -1
   var encodedLength: Int = -1

   override val sbeName = declaration.simpleName()
   override val name = declaration.simpleName()
   override val size: Int get() = encodedLength

   init {
      var currentOffset = 0
      val sortedFields = fields.sortedByDescending { it.type.size }

      sortedFields.forEachIndexed { index, field ->
         if (usePadding) {
            // Align to 8-byte boundary
            val padding = (8 - (currentOffset % 8)) % 8
            currentOffset += padding
         }
         field.offset = currentOffset
         field.sbeOrder = index + 1
         currentOffset += field.type.size
      }

      // encodedLength is the actual SBE size (without final padding)
      encodedLength = currentOffset

      // blockLength includes final padding (only used internally, not in SBE schema)
      blockLength = if (usePadding) {
         val remainder = currentOffset % 8
         if (remainder == 0) currentOffset else currentOffset + (8 - remainder)
      } else {
         currentOffset
      }
   }

   override val packageStr = declaration.packageName()
   override val defaultValue = "new$name()"
   override val randomValue = "random$name()"

   override val imports by lazy {
      buildSet {
         for (field in fields) {
            addAll(field.type.imports)
         }
         add(if (packageStr.isNotEmpty()) "${packageStr}.${name}" else name)
         val pkg = packageStr.ifEmpty { name }
         add("${pkg}.${name}RW")
         add("${pkg}.${name}R")
         add("${pkg}.${name}W")
      }
   }

   override fun howToEncodeFromSource(encoder: String, field: String, instance: String): String {
      val sb = StringBuilder()
      sb.appendLine("val $field = $encoder.$field()")
      for (it in fields) {
         val value = it.type.convertToSbe("$instance.$field.${it.name}")
         sb.appendLine("$field.${it.name}($value)")
      }
      return sb.removeSuffix("\n").toString()
   }

   override fun howToDecodeIntoTarget(decoder: String, field: String, instance: String): String {
      val sb = StringBuilder()
      sb.appendLine("val $field = $decoder.$field()")
      for (it in fields) {
         val decodedValue = it.type.convertFromSbe("$field.${it.name}()")
         sb.appendLine("$instance.$field.${it.name} = $decodedValue")
      }
      return sb.removeSuffix("\n").toString()
   }

   override fun fwSetter(encoder: String, field: String): String {
      val calls = fields.joinToString(".") { "${it.name}($field.${it.name})" }
      return "${field}FW.$calls"
   }

   override fun fwGetter(decoder: String, field: String): String {
      val fieldDecodes = fields.joinToString(", ") {
         val decodedValue = it.type.convertFromSbe("$decoder.$field().${it.name}()")
         "${it.name} = $decodedValue"
      }
      return "${name}($fieldDecodes)"
   }
}