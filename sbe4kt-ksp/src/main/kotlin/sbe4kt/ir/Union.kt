package sbe4kt.ir

internal data class Union(
   val allowedTypes: List<Message>,
   val fieldName: String,
   val typeName: String = "Any",
   val typePackage: String = "",
) : IR.Leaf {
   val maxSize: Int = allowedTypes.maxOf { it.size }

   override val sbeName = "ByteArray$maxSize"
   override val name = typeName
   override val size: Int = maxSize
   override val packageStr = typePackage
   override val defaultValue = allowedTypes.first().defaultValue
   override val randomValue = allowedTypes.first().randomValue

   override val imports by lazy {
      buildSet {
         for (type in allowedTypes) {
            addAll(type.imports)
         }
         if (typeName != "Any" && typePackage.isNotEmpty()) {
            add("$typePackage.$typeName")
         }
      }
   }
}
