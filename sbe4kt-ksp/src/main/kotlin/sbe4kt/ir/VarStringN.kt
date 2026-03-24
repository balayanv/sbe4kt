package sbe4kt.ir

import sbe4kt.META.Encoding

internal data class VarStringN(val encoding: Encoding) : IR.Leaf {
   override val sbeName = if (encoding == Encoding.UTF8) "varStrU" else "varStr"
   override val name = "StringBuilder"
   override val defaultValue = "StringBuilder()"
   override val randomValue = "Random.stringBuilder()"
   override val size = -1
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random", "sbe4kt.lib.stringBuilder")

   override fun fwSetter(encoder: String, field: String) = "$encoder.$field($field)"
   override fun fwGetter(decoder: String, field: String) = "$decoder.$field()"

   override fun assignToInstanceField(instance: String, field: String, value: String) =
      "$instance.$field.clear().append($value)"
}
