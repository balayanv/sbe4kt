package sbe4kt.ir

internal data object IntArrayListGroup : IR.Leaf, RepeatingGroup {
   override val elementType: IR.Leaf = Int32
   override val sbeName = "int32"
   override val name = "IntArrayList"
   override val size = -1
   override val defaultValue = "IntArrayList()"
   override val randomValue = "IntArrayList().apply { repeat(Random.nextInt(1, 5)) { addInt(Random.nextInt()) } }"
   override val packageStr = ""
   override val imports = setOf(
      "kotlin.random.Random",
      "org.agrona.collections.IntArrayList"
   )
}
