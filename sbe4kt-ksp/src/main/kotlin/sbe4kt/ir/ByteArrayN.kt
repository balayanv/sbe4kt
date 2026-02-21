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
}
