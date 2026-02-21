package sbe4kt.ir

import sbe4kt.META.Encoding

/**
 * Represents a fixed-size String type with encoding.
 * In SBE schema, this generates: <type name="CharArrayN" primitiveType="char" length="N" characterEncoding="encoding"/>
 */
internal data class StringN(val length: Int, val encoding: Encoding = Encoding.ASCII) : IR.Leaf {
   override val sbeName = "CharArray$length${if (encoding == Encoding.UTF8) "U" else ""}"
   override val name = "String"
   override val defaultValue = "\"\""
   override val randomValue = "Random.nextStr($length)"
   override val size = length
   override val packageStr = ""
   override val imports: Set<String> = setOf(
      "sbe4kt.lib.nextStr"
   )
}
