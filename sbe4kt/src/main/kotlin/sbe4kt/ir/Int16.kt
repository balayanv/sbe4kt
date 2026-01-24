package sbe4kt.ir

internal data object Int16 : IR.Leaf {
   override val sbeName = "int16"
   override val name = "Short"
   override val defaultValue = "0.toShort()"
   override val randomValue = "Random.nextInt().toShort()"
   override val size = 2
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random")
}