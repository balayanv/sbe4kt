package sbe4kt.ir

import sbe4kt.META.Encoding

/**
 * Represents a fixed-size String type with encoding.
 * In SBE schema, this generates: <type name="CharArrayN" primitiveType="char" length="N" characterEncoding="encoding"/>
 */
internal data class StringN(val length: Int, val encoding: Encoding = Encoding.ASCII) : IR.Leaf {
   override val sbeName = "CharArray$length${if (encoding == Encoding.UTF8) "U" else ""}"
   override val name = "StringBuilder"
   override val defaultValue = "StringBuilder()"
   override val randomValue = "Random.stringBuilder()"
   override val size = length
   override val packageStr = ""
   override val imports: Set<String> = setOf(
      "kotlin.random.Random",
      "sbe4kt.lib.stringBuilder",
   )

   override fun encodeField(encoder: String, field: String, value: String) = "$encoder.$field($value)"

   override fun decodeField(decoder: String, field: String) = "$decoder.$field()"

   override fun fwSetter(encoder: String, field: String) = "$encoder.${field}($field)"

   override fun fwGetter(decoder: String, field: String) = "$decoder.${field}()"

   override fun assignToInstanceField(instance: String, field: String, value: String) =
      "$instance.$field.clear().append($value)"
}
