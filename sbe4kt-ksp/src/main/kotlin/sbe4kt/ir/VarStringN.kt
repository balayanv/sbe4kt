package sbe4kt.ir

import sbe4kt.META.Encoding

internal data class VarStringN(val encoding: Encoding) : IR.Leaf {
   override val sbeName = if (encoding == Encoding.UTF8) "varStringEncoding" else "varAsciiEncoding"
   override val name = "String"
   override val defaultValue = "\"\""
   override val randomValue = "Random.nextStr(8)"
   override val size = -1
   override val packageStr = ""
   override val imports = setOf("sbe4kt.lib.nextStr")

   override fun fwSetter(encoder: String, field: String) = "$encoder.$field($field)"
   override fun fwGetter(decoder: String, field: String) = "$decoder.$field()"
}
