package sbe4kt.ir

internal data class DoubleArrayN(val length: Int) : IR.Leaf {
   override val sbeName = "DoubleArray$length"
   override val name = "DoubleArray"
   override val defaultValue = "DoubleArray($length)"
   override val randomValue = "DoubleArray($length) { Random.nextDouble() }"
   override val size = length * 8
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random", "sbe4kt.lib.DoubleArrayCodec", "sbe4kt.lib.DoubleArrayFW")
}
