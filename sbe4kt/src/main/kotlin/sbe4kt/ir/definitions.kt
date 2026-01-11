package sbe4kt.ir

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import sbe4kt.util.getSbeFieldName


internal data class IrContext(
   var sourceSet: String = "main",
   val types: ArrayList<IrType> = ArrayList()
) {
   fun add(irType: IrType) {
      if (irType.id == -1) {
         irType.id = getNextAvailableId()
      }

      ids.add(irType.id)
      types.add(irType)
   }

   fun prepare() {
      types.sortBy { it.id }
   }

   val ids = HashSet<Int>()
   var id = 1

   private fun getNextAvailableId(): Int {
      while (ids.contains(id)) id++
      return id
   }
}

internal data class IrType(
   val type: KSClassDeclaration
) {
   val packageName: String = type.packageName.asString()
   val className: String = type.simpleName.asString()
   val annotationArgs = type.annotations.find { it.shortName.asString() == "SBE" }!!.arguments
   var id: Int = annotationArgs.find { it.name?.asString() == "id" }?.value as? Int ?: -1
   val usePadding: Boolean = annotationArgs.find { it.name?.asString() == "usePadding" }?.value as? Boolean ?: false
   val fields: List<IrField> = getIrFields()

   private fun getIrFields(): List<IrField> {
      val fields = ArrayList<IrField>()

      type.getAllProperties().forEachIndexed { index, prop ->
         fields.add(IrField(prop, index))
      }

      fields.sortByDescending { it.type.size }
      fields.forEachIndexed { index, field -> field.id = index + 1 }
      return fields
   }
}


internal data class IrField(
   val prop: KSPropertyDeclaration,
   val declarationOrder: Int = 0
) {
   val name: String = getSbeFieldName(prop.simpleName.asString())
   var id: Int = -1
   val type: IR = prop.type.resolve().declaration.qualifiedName!!.asString().toSbeType()
}
