package sbe4kt.ir

internal data object LongArrayListGroup : IR.Leaf, RepeatingGroup {
   override val elementType: IR.Leaf = Int64
   override val sbeName = "int64"
   override val name = "LongArrayList"
   override val size = -1
   override val defaultValue = "LongArrayList()"
   override val randomValue = "LongArrayList().apply { repeat(Random.nextInt(1, 5)) { addLong(Random.nextLong()) } }"
   override val packageStr = ""
   override val imports = setOf(
      "kotlin.random.Random",
      "org.agrona.collections.LongArrayList"
   )
}
