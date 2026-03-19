package sbe4kt.ir

internal sealed interface RepeatingGroup {
   val elementType: IR.Leaf
}

internal data class List(override val elementType: IR.Leaf) : IR.Leaf, RepeatingGroup {
   override val sbeName = elementType.sbeName
   override val name = "List<${elementType.name}>"
   override val size = -1
   override val defaultValue = "emptyList()"
   override val randomValue = when (elementType) {
      is Struct -> "List(Random.nextInt(1, 5)) { random${elementType.name}() }"
      is VarStringN -> "List(Random.nextInt(1, 5)) { Random.nextStr(8) }"
      else -> "List(Random.nextInt(1, 5)) { ${elementType.randomValue} }"
   }
   override val packageStr = ""
   override val imports get() = elementType.imports
}
