package sbe4kt.ir

internal data object Char : IR.Leaf {
   override val sbeName = "char"
   override val name = "Char"
   override val defaultValue = "'\\u0000'"
   override val randomValue = "Random.nextChar()"
   override val size = 1
   override val packageStr = ""
   override val imports = setOf(
      "kotlin.random.Random",
      "sbe4kt.lib.nextChar"
   )
   override fun convertToSbe(value: String) = "$value.code.toByte()"
   override fun convertFromSbe(value: String) = "$value.toInt().toChar()"
}