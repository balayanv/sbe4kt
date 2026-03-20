package sbe4kt.ir

internal data class Bool(
   val compressedFlags: CompressedFlags? = null,
   val bitPosition: Int = -1,
) : IR.Leaf {
   override val sbeName = if (compressedFlags != null) "bit" else "int8"
   override val name = "Boolean"
   override val defaultValue = "false"
   override val randomValue = "Random.nextBoolean()"
   override val size = if (compressedFlags != null) 0 else 1
   override val packageStr = ""
   override val imports: Set<String> = if (compressedFlags != null) {
      setOf("kotlin.random.Random")
   } else {
      setOf("sbe4kt.lib.convertToByte", "sbe4kt.lib.convertToBoolean", "kotlin.random.Random")
   }

   val isCompressed: Boolean get() = compressedFlags != null

   // compressed: SBE flags encoder/decoder works with boolean directly
   // non-compressed: SBE field uses int8, needs convertToByte/convertToBoolean
   override fun convertToSbe(value: String) = if (isCompressed) value else "$value.convertToByte()"
   override fun convertFromSbe(value: String) = if (isCompressed) value else "$value.convertToBoolean()"
}
