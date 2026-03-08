package sbe4kt.ir

internal data class FloatArrayN(val length: Int) : IR.Leaf {
   override val sbeName = "FloatArray$length"
   override val name = "FloatArray"
   override val defaultValue = "FloatArray($length)"
   override val randomValue = "FloatArray($length) { Random.nextFloat() }"
   override val size = length * 4
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random", "sbe4kt.lib.FloatArrayCodec", "sbe4kt.lib.FloatArrayFW")
}
