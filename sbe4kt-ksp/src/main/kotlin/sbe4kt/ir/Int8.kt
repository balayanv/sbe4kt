package sbe4kt.ir

internal data object Int8 : IR.Leaf {
   override val sbeName = "int8"
   override val name = "Byte"
   override val defaultValue = "0.toByte()"
   override val randomValue = "Random.nextInt().toByte()"
   override val size = 1
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random")
}