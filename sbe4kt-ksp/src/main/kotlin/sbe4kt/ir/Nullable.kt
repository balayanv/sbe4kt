package sbe4kt.ir

internal data class Nullable(val innerType: IR.Leaf) : IR.Leaf {
   override val sbeName = innerType.sbeName
   override val name = "${innerType.name}?"
   override val defaultValue = "null"
   override val randomValue = "if (Random.nextBoolean()) ${innerType.randomValue} else null"
   override val size = innerType.size
   override val packageStr = innerType.packageStr
   override val imports get() = innerType.imports + setOf("kotlin.random.Random")

   override fun convertToSbe(value: String) = innerType.convertToSbe(value)
   override fun convertFromSbe(value: String) = innerType.convertFromSbe(value)
   override fun encodeField(encoder: String, field: String, value: String) = innerType.encodeField(encoder, field, value)
   override fun decodeField(decoder: String, field: String) = innerType.decodeField(decoder, field)
   override fun fwSetter(encoder: String, field: String) = innerType.fwSetter(encoder, field)
   override fun fwGetter(decoder: String, field: String) = innerType.fwGetter(decoder, field)
}
