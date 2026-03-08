package sbe4kt.ir

internal data class IntArrayN(val length: Int) : IR.Leaf {
   override val sbeName = "IntArray$length"
   override val name = "IntArray"
   override val defaultValue = "IntArray($length)"
   override val randomValue = "IntArray($length) { Random.nextInt() }"
   override val size = length * 4
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random", "sbe4kt.lib.IntArrayCodec", "sbe4kt.lib.IntArrayFW")
}
