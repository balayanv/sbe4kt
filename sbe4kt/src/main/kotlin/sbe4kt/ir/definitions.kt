package sbe4kt.ir

import com.google.devtools.ksp.symbol.*
import sbe4kt.ir.IR.Composite.CompositeField
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
      types.sortBy { it.id }
      types.forEach { it.prepare() }
   }
}

internal data class IrMessage(
   val type: KSClassDeclaration
) {
   val packageName: String = type.packageName.asString()
   val className: String = type.simpleName.asString()
   val annotationArgs = type.annotations.find { it.shortName.asString() == "SBE" }!!.arguments.validateSbeAnnotation()
   var id: Int = annotationArgs.find { it.name?.asString() == "id" }?.value as? Int ?: -1
   val usePadding: Boolean = annotationArgs.find { it.name?.asString() == "usePadding" }?.value as? Boolean ?: false

   var blockLength: Int = -1
   val fields: List<IrField> = getIrFields()

   fun prepare() {
      var currentOffset = 0
      val sortedFields = fields.sortedByDescending { it.type.size }

      sortedFields.forEachIndexed { index, field ->
         if (usePadding) {
            val padding = (8 - (currentOffset % 8)) % 8
            currentOffset += padding
         }
         field.offset = currentOffset
         field.sbeOrder = index + 1
         currentOffset += field.type.size
      }

      blockLength = if (usePadding) {
         val remainder = currentOffset % 8
         if (remainder == 0) currentOffset else currentOffset + (8 - remainder)
      } else {
         currentOffset
      }
   }

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

   if (declaration is KSTypeAlias) {
      return resolveType(declaration.type.resolve(), parentClassName)
   }

   if (declaration is KSClassDeclaration) {
      if (declaration.isValueClass()) {
         val underlyingType = resolveType(declaration.primaryConstructor!!.parameters.first().type.resolve(), parentClassName)
         return IR.ValueClass(declaration, underlyingType)
      }

      if (declaration.isEnum()) {
         return IR.Enum(declaration)
      }
   }

   // primitive
   return when (declaration.qualifiedName()) {
      "kotlin.Boolean" -> IR.Boolean
      "kotlin.Char" -> IR.Char
      "kotlin.Byte" -> IR.Int8
      "kotlin.Short" -> IR.Int16
      "kotlin.Int" -> IR.Int32
      "kotlin.Long" -> IR.Int64
      "kotlin.Float" -> IR.Float
      "kotlin.Double" -> IR.Double
      else -> throw IllegalArgumentException("Unsupported type: ${declaration.qualifiedName()}")
   }
}

private fun List<KSValueArgument>.validateSbeAnnotation() = apply {
   val usePadding = this.find { it.name?.asString() == "usePadding" }?.value as? Boolean ?: false
   val allowVarLengthFields = this.find { it.name?.asString() == "allowVarLengthFields" }?.value as? Boolean ?: false

   require(!(usePadding && allowVarLengthFields)) {
      "usePadding and allowVarLengthFields cannot both be true. Padding requires fixed field positions which conflicts with variable length fields."
   }
}
