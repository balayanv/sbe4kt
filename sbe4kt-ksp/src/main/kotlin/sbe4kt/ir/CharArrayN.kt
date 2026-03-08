package sbe4kt.ir

internal data class CharArrayN(val length: Int) : IR.Leaf {
   override val sbeName = "CharArray$length"
   override val name = "CharArray"
   override val defaultValue = "CharArray($length)"
   override val randomValue = "CharArray($length) { Random.nextInt(32, 127).toChar() }"
   override val size = length
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random", "sbe4kt.lib.CharArrayCodec", "sbe4kt.lib.CharArrayFW")
}
