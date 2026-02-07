package sbe4kt.ir

internal data object Bool : IR.Leaf {
   override val sbeName = "int8"
   override val name = "Boolean"
   override val defaultValue = "false"
   override val randomValue = "Random.nextBoolean()"
   override val size = 1
   override val packageStr = ""
   override val imports: Set<String> = mutableSetOf(
      "sbe4kt.lib.convertToByte",
      "sbe4kt.lib.convertToBoolean",
      "kotlin.random.Random",
   )

   override fun convertToSbe(value: String) = "$value.convertToByte()"
   override fun convertFromSbe(value: String) = "$value.convertToBoolean()"
}