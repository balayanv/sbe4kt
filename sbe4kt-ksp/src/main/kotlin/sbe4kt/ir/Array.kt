package sbe4kt.ir

internal data class Array(override val elementType: IR.Leaf) : IR.Leaf, RepeatingGroup {
   override val sbeName = elementType.sbeName
   override val name = "Array<${elementType.name}>"
   override val size = -1
   override val defaultValue = "emptyArray()"
   override val randomValue = when (elementType) {
      is Struct -> "Array(Random.nextInt(1, 9)) { random${elementType.name}() }"
      is VarStringN -> "Array(Random.nextInt(1, 9)) { Random.nextStr(8) }"
      else -> "Array(Random.nextInt(1, 9)) { ${elementType.randomValue} }"
   }
   override val packageStr = ""
   override val imports get() = elementType.imports
}
