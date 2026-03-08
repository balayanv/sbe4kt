package sbe4kt.ir

internal data class StringN(val length: Int) : IR.Leaf {
   override val sbeName = "CharArray$length"
   override val name = "String"
   override val defaultValue = "\"\""
   override val randomValue = "Random.nextStr($length)"
   override val size = length
   override val packageStr = ""
   override val imports: Set<String> = setOf(
      "sbe4kt.lib.nextStr",
      "sbe4kt.lib.CharArrayCodec",
      "sbe4kt.lib.CharArrayFW",
      "sbe4kt.lib.branchlessMin",
   )
}
