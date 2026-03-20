package sbe4kt.ir

internal sealed interface RepeatingGroup {
   val elementType: IR.Leaf
}

internal data class ArrayListGroup(override val elementType: IR.Leaf) : IR.Leaf, RepeatingGroup {
   override val sbeName = elementType.sbeName
   override val name = "ArrayList<${elementType.name}>"
   override val size = -1
   override val defaultValue = "ArrayList()"
   override val randomValue = when (elementType) {
      is Struct -> "arrayListOf(${elementType.randomValue}, ${elementType.randomValue})"
      else -> "arrayListOf(${elementType.randomValue}, ${elementType.randomValue})"
   }
   override val packageStr = ""
   override val imports get() = elementType.imports
}
