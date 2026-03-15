package sbe4kt.ir

internal data class Array(override val elementType: IR.Leaf) : IR.Leaf, RepeatingGroup {
   override val sbeName = elementType.sbeName
   override val name = "Array<${elementType.name}>"
   override val size = -1
   override val defaultValue = "emptyArray()"
   override val randomValue = "Array(Random.nextInt(1, 5)) { random${elementType.name}() }"
   override val packageStr = ""
   override val imports get() = elementType.imports
}
