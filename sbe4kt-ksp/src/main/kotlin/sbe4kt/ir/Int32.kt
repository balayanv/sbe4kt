package sbe4kt.ir

internal data object Int32 : IR.Leaf {
   override val sbeName = "int32"
   override val name = "Int"
   override val defaultValue = "0"
   override val randomValue = "Random.nextInt()"
   override val size = 4
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random")
}