package sbe4kt.ir

import com.google.devtools.ksp.symbol.*
import sbe4kt.util.*


internal data class IrSchema(
   var sourceSet: String = "main",
   val types: ArrayList<IrMessage> = ArrayList()
) {

   val ids = HashSet<Int>()
   var id = 1

   private fun getNextAvailableId(): Int {
      while (ids.contains(id)) id++
      return id
   }

   fun add(irMessage: IrMessage) {
      if (irMessage.id == -1) {
         irMessage.id = getNextAvailableId()
      }

      ids.add(irMessage.id)
      types.add(irMessage)
   }

   fun prepare() {
      // todo we need to be able to handle nesting, using one annotated type from another, recursively processing them
      // some sort of graph needs to be composed which finds out which types depend on which (if any)
      // and start processing types that are not depending on others
      types.sortBy { it.id }

      // Compute offsets and block length for each type
      types.forEach { type ->
         if (type.usePadding) {
            computePaddedOffsets(type)
         } else {
            computePackedOffsets(type)
         }
      }
   }

   private fun computePaddedOffsets(type: IrMessage) {
      var currentOffset = 0

      // Sort fields by size (descending) for better alignment
      val sortedFields = type.fields.sortedByDescending { it.type.size }

      sortedFields.forEachIndexed { index, field ->
         // Align to 8-byte boundary if needed
         val alignment = 8
         val padding = (alignment - (currentOffset % alignment)) % alignment
         if (padding > 0) {
            currentOffset += padding
         }

         field.offset = currentOffset
         field.sbeOrder = index + 1
         currentOffset += field.type.size
      }

      // Ensure block length is multiple of 8
      // If currentOffset is already a multiple of 8, we don't need padding
      // Otherwise, add padding to reach the next multiple of 8
      val remainder = currentOffset % 8
      type.blockLength = if (remainder == 0) currentOffset else currentOffset + (8 - remainder)
   }

   private fun computePackedOffsets(type: IrMessage) {
      var currentOffset = 0

      // Sort fields by size (descending) for optimal packing
      val sortedFields = type.fields.sortedByDescending { it.type.size }

      sortedFields.forEachIndexed { index, field ->
         field.offset = currentOffset
         field.sbeOrder = index + 1
         currentOffset += field.type.size
      }

      type.blockLength = currentOffset
   }
}

/**
 * Each IrType corresponds to an <sbe:message...
 */
internal data class IrMessage(
   val type: KSClassDeclaration
) {
   val packageName: String = type.packageName.asString()
   val className: String = type.simpleName.asString()
   val annotationArgs = type.annotations.find { it.shortName.asString() == "Message" }!!.arguments.validateSbeAnnotationValues()
   var id: Int = annotationArgs.find { it.name?.asString() == "id" }?.value as? Int ?: -1
   val usePadding: Boolean = annotationArgs.find { it.name?.asString() == "usePadding" }?.value as? Boolean ?: false

   // Computed in prepare() - total block size must be multiple of 8 when usePadding is true
   // This ensures next message in buffer starts at aligned position
   var blockLength: Int = -1
   val fields: List<IrField> = getIrFields()

   private fun getIrFields(): List<IrField> {
      val fields = ArrayList<IrField>()

      type.getAllProperties().forEachIndexed { index, prop ->
         fields.add(IrField(prop, index, className))
      }

      fields.sortByDescending { it.type.size }
      fields.forEachIndexed { index, field -> field.id = index + 1 }
      return fields
   }
}

private fun List<KSValueArgument>.validateSbeAnnotationValues() = apply {
   val usePadding = this.find { it.name?.asString() == "usePadding" }?.value as? Boolean ?: false
   val allowVarLengthFields = this.find { it.name?.asString() == "allowVarLengthFields" }?.value as? Boolean ?: false

   require(!(usePadding && allowVarLengthFields)) {
      "usePadding and allowVarLengthFields cannot both be true. Padding requires fixed field positions which conflicts with variable length fields."
   }
}


/**
 * Each IrField corresponds to a <field name=...
 */
internal data class IrField(
   val prop: KSPropertyDeclaration,
   val declarationOrder: Int = 0,
   val parentClassName: String? = null
) {
   val name: String = prop.simpleName.asString().validateNotReservedWord()
   var id: Int = -1
   val type: IR = getFieldType()
   var offset: Int = -1
   var sbeOrder: Int = -1

   fun addPackageIfNecessary(currentPackage: String, whereToAdd: MutableCollection<String>) {
      val packageStr = type.packageStr

      // Add imports for types from different packages
      if (packageStr.isNotEmpty() && packageStr != currentPackage) {
         // Add full type name, not just package
         val importStr = when (type) {
            is IR.Composite -> "$packageStr.${type.name}"
            is IR.Enum -> "$packageStr.${type.name}"
            is IR.ValueClass -> "$packageStr.${type.name}"
            else -> packageStr
         }
         whereToAdd.add(importStr)
      }

      // For composite types, also add imports for their component types
      if (type is IR.Composite) {
         type.fields.forEach { compositeField ->
            val compPackageStr = compositeField.type.packageStr
            if (compPackageStr.isNotEmpty() && compPackageStr != currentPackage) {
               val importStr = when (compositeField.type) {
                  is IR.Enum -> "$compPackageStr.${compositeField.type.name}"
                  is IR.ValueClass -> "$compPackageStr.${compositeField.type.name}"
                  else -> compPackageStr
               }
               whereToAdd.add(importStr)
            }
         }
      }
   }

   private fun getFieldType(): IR {
      val resolvedType = prop.type.resolve()

      if (prop.isComposite()) {
         // Resolve as composite type

         val compositeDeclaration = resolvedType.declaration as KSClassDeclaration

         compositeDeclaration.requireMutableDataClass()

         val compositeFields = compositeDeclaration.getAllProperties().map { compProp ->
            CompositeField(
               name = compProp.simpleName.asString(),
               type = resolveType(compProp.type.resolve())
            )
         }

         return IR.Composite(compositeDeclaration, compositeFields.toList())
      }

      return resolveType(resolvedType, parentClassName)
   }
}


private fun resolveType(resolvedType: KSType, parentClassName: String? = null): IR {
   val declaration = resolvedType.declaration

   // type alias
   if (declaration is KSTypeAlias) {
      return resolveType(declaration.type.resolve(), parentClassName)
   }

   if (declaration is KSClassDeclaration) {
      // value class
      if (declaration.isValueClass()) {
         val underlyingType = resolveType(declaration.primaryConstructor!!.parameters.first().type.resolve(), parentClassName)
         return IR.ValueClass(declaration, underlyingType)
      }

      // enums - check if it's a nested enum by looking at parent
      if (declaration.isEnum()) {
         // Check if this enum is nested inside a class
         return IR.Enum(declaration)
      }
   }

   // primitive
   return declaration.qualifiedName().toPrimitiveIrType()
}
