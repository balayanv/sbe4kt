package sbe4kt.ir

/**
 * Represents a fixed-size ByteArray type.
 * In SBE schema, this generates: <type name="BytesN" primitiveType="uint8" length="N"/>
 */
internal data class ByteArrayN(val length: Int) : IR.Leaf {
   override val sbeName = "ByteArray$length"
   override val name = "ByteArray"
   override val defaultValue = "ByteArray($length)"
   override val randomValue = "ByteArray($length) { Random.nextInt().toByte() }"
   override val size = length
   override val packageStr = ""
   override val imports = setOf("kotlin.random.Random")

   override fun encodeField(encoder: String, field: String, value: String) = "$encoder.put$field($value, 0, $length)"

   override fun decodeField(decoder: String, field: String) = "$decoder.get$field()"

   override fun howToEncodeFromSource(encoder: String, field: String, instance: String) = "$encoder.put${field.replaceFirstChar { it.uppercase() }}($instance.$field, 0, $length)"

   override fun howToDecodeIntoTarget(decoder: String, field: String, instance: String) = "$decoder.get${field.replaceFirstChar { it.uppercase() }}($instance.$field, 0, $length)"

   override fun fwSetter(encoder: String, field: String) = "$encoder.put${field.replaceFirstChar { it.uppercase() }}($field, 0, $length)"

   override fun fwGetter(decoder: String, field: String) = "ByteArray($length).also { $decoder.get${field.replaceFirstChar { it.uppercase() }}(it, 0, $length) }"
}
