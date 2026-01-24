package sbe4kt.ir

internal data object Int64 : IR.Leaf {
   override val sbeName = "int64"
   override val name = "Long"
   override val defaultValue = "0L"
   override val randomValue = "Random.nextLong()"
   override val size = 8
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random")
}