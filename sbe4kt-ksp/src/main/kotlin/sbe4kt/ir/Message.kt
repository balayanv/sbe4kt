package sbe4kt.ir

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import sbe4kt.migration.PrevField
import sbe4kt.migration.PrevMessage
import sbe4kt.util.allowNullableTypes
import sbe4kt.util.compactFlags
import sbe4kt.util.getFieldIRs
import sbe4kt.util.id
import sbe4kt.util.importName
import sbe4kt.util.packageName
import sbe4kt.util.sbeAnnotation
import sbe4kt.util.simpleName
import sbe4kt.util.usePadding
import kotlin.Array

internal data class Message(
   val declaration: KSClassDeclaration,
   val previousMessage: PrevMessage? = null,
) : IR.Leaf, IR.Root{
   val annotation: KSAnnotation = declaration.sbeAnnotation()!!
   var id: Int = annotation.id()
   val usePadding: Boolean = annotation.usePadding()
   val allowNullableTypes: Boolean = annotation.allowNullableTypes()
   val compactFlags: Boolean = annotation.compactFlags()
   var blockLength: Int = -1
   val fields = declaration.getFieldIRs()
   val deprecatedFields: MutableList<PrevField> = mutableListOf()
   val hasVariableFields: Boolean get() = fields.any { it.type.size < 0 }

   override val name = declaration.simpleName()

   // When used as a nested field type in SBE schema, use ByteArrayN
   override val sbeName: String get() = "ByteArray$size"

   // When used as a nested field, include 8-byte header in size
   override val size: Int get() = 8 + blockLength

   override val defaultValue: String = "new${name}()"
   override val randomValue: String = "random${name}()"
   override val packageStr: String = declaration.packageName()

   override val imports: Set<String> = allImports().toSet()

   init {
      fields.onEach { require(!it.isNullable || allowNullableTypes) { "nullable field '${it.name}' in message '${declaration.simpleName()}' is not allowed" } }

      if (previousMessage != null) {
         initWithPreviousSchema(previousMessage)
      } else {
         initFresh()
      }
   }

   private fun initFresh() {
      var currentOffset = 0
      var sbeOrder = 1

      // Fixed fields first — sorted largest-to-smallest for optimal alignment
      fields
         .filter { it.type.size >= 0 }
         .sortedByDescending { it.type.size }
         .forEach { field ->
            if (usePadding) {
               val padding = (8 - (currentOffset % 8)) % 8
               currentOffset += padding
            }
            field.offset = currentOffset
            field.sbeOrder = sbeOrder++
            currentOffset += field.type.size
         }

      blockLength = if (usePadding) {
         val remainder = currentOffset % 8
         if (remainder == 0) currentOffset else currentOffset + (8 - remainder)
      } else {
         currentOffset
      }

      // Variable fields after — groups first (SBE requirement), then data
      val varFields = fields.filter { it.type.size < 0 }
      val groupFields = varFields.filter { it.type is RepeatingGroup }
      val dataFields = varFields.filter { it.type !is RepeatingGroup }
      (groupFields + dataFields).forEach { field ->
         field.offset = -1
         field.sbeOrder = sbeOrder++
      }
   }

   private fun initWithPreviousSchema(prev: PrevMessage) {
      val prevByName = prev.fieldsByName
      val currentFieldNames = fields.map { it.name }.toSet()

      // existing fields: preserve their previous sbeOrder and id
      val existingFields = fields.filter { it.name in prevByName }
      val newFields = fields.filter { it.name !in prevByName }

      for (field in existingFields) {
         val prevField = prevByName[field.name]!!
         field.sbeOrder = prevField.sbeOrder
         field.id = prevField.id
      }

      // deprecated fields: all previous fields not in the current class (both newly removed and already deprecated)
      val removedPrevFields = prev.fields.filter { it.name !in currentFieldNames }
      deprecatedFields.addAll(removedPrevFields)

      // build a size lookup for all previous fields (current fields use actual IR size, deprecated use parsed size)
      val prevFieldSizes = prev.fields.associate { pf ->
         pf.name to (fields.find { it.name == pf.name }?.type?.size ?: sbeTypeSize(pf.type))
      }

      // recompute offsets: walk all previous fields in sbeOrder, then append new fields
      // this handles both padded (explicit offset) and non-padded (sequential) cases correctly
      var currentOffset = 0
      for (prevField in prev.fields.sortedBy { it.sbeOrder }) {
         if (usePadding && prevField.offset != null) {
            currentOffset = prevField.offset
         } else if (usePadding) {
            val padding = (8 - (currentOffset % 8)) % 8
            currentOffset += padding
         }

         // assign offset to the matching current field (if it still exists)
         val currentField = fields.find { it.name == prevField.name }
         currentField?.offset = currentOffset

         currentOffset += prevFieldSizes[prevField.name] ?: sbeTypeSize(prevField.type)
      }

      // new fields: append after previous block
      var nextSbeOrder = prev.maxSbeOrder + 1
      var nextFieldId = prev.maxFieldId + 1

      newFields.sortedByDescending { it.type.size }.forEach { field ->
         if (usePadding) {
            val padding = (8 - (currentOffset % 8)) % 8
            currentOffset += padding
         }
         field.offset = currentOffset
         field.sbeOrder = nextSbeOrder++
         field.id = nextFieldId++
         currentOffset += field.type.size
      }

      blockLength = if (usePadding) {
         val remainder = currentOffset % 8
         if (remainder == 0) currentOffset else currentOffset + (8 - remainder)
      } else {
         currentOffset
      }

      // blockLength must be at least as large as previous (deprecated fields still occupy space)
      if (prev.blockLength != null && blockLength < prev.blockLength) {
         blockLength = prev.blockLength
      }
   }

   fun sourceFiles(): Array<KSFile> {
      // todo cleanup
      val dependentFiles = mutableSetOf(declaration.containingFile!!)
      fields.forEach { field ->
         when (val type = field.type) {
            is Struct -> type.declaration.containingFile?.let { dependentFiles.add(it) }
            is Message -> type.declaration.containingFile?.let { dependentFiles.add(it) }
            is RepeatingGroup -> (type.elementType as Struct).declaration.containingFile?.let { dependentFiles.add(it) }
            else -> {} // Primitive types don't have source files
         }
      }

      return dependentFiles.toTypedArray()
   }

   companion object {
      internal fun sbeTypeSize(sbeType: String): Int = when (sbeType) {
         "int64", "uint64", "double" -> 8
         "int32", "uint32", "float" -> 4
         "int16", "uint16" -> 2
         "int8", "uint8" -> 1
         "char" -> 1
         else -> {
            val byteArrayMatch = Regex("ByteArray(\\d+)").matchEntire(sbeType)
            val charArrayMatch = Regex("CharArray(\\d+)U?").matchEntire(sbeType)
            when {
               byteArrayMatch != null -> byteArrayMatch.groupValues[1].toInt()
               charArrayMatch != null -> charArrayMatch.groupValues[1].toInt()
               else -> 8
            }
         }
      }
   }

   fun allImports(): Set<String> {
      val imports = mutableSetOf<String>()

      val packageName = packageStr
      imports.add("org.agrona.DirectBuffer")
      imports.add("org.agrona.MutableDirectBuffer")
      imports.add("org.agrona.concurrent.UnsafeBuffer")
      if (hasVariableFields) imports.add("org.agrona.ExpandableArrayBuffer")
      imports.add("sbe4kt.MessageHeaderEncoder")
      imports.add("sbe4kt.MessageHeaderDecoder")
      imports.add("sbe4kt.lib.SbeLayoutRow")
      imports.add("sbe4kt.lib.toCSV")
      imports.add("sbe4kt.${name}Encoder")
      imports.add("sbe4kt.${name}Decoder")
      imports.add(declaration.importName())
      fields.onEach { imports.addAll(it.type.imports) }

      // Union allowed types' encoder/decoder imports are already included via field.type.imports chain

      return imports
         .filter { it.substringBeforeLast('.') != packageName && it != packageName }
         .sorted()
         .toSet()
   }
}