package sbe4kt.ir

internal data object Double : IR.Leaf {
   override val sbeName = "double"
   override val name = "Double"
   override val defaultValue = "0.0"
   override val randomValue = "Random.nextDouble()"
   override val size = 8
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random")
}