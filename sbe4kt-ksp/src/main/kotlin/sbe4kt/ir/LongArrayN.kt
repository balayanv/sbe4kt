package sbe4kt.ir

internal data class LongArrayN(val length: Int) : IR.Leaf {
   override val sbeName = "LongArray$length"
   override val name = "LongArray"
   override val defaultValue = "LongArray($length)"
   override val randomValue = "LongArray($length) { Random.nextLong() }"
   override val size = length * 8
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random", "sbe4kt.lib.LongArrayCodec", "sbe4kt.lib.LongArrayFW")
}
