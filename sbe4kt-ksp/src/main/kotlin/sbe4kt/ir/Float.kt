package sbe4kt.ir

internal data object Float : IR.Leaf {
   override val sbeName = "float"
   override val name = "Float"
   override val defaultValue = "0.0f"
   override val randomValue = "Random.nextFloat()"
   override val size = 4
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random")
}