package sbe4kt.ir

internal data class ShortArrayN(val length: Int) : IR.Leaf {
   override val sbeName = "ShortArray$length"
   override val name = "ShortArray"
   override val defaultValue = "ShortArray($length)"
   override val randomValue = "ShortArray($length) { Random.nextInt().toShort() }"
   override val size = length * 2
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random", "sbe4kt.lib.ShortArrayCodec", "sbe4kt.lib.ShortArrayFW")
}
