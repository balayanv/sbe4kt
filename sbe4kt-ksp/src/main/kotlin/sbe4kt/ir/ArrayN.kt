package sbe4kt.ir

internal data class ArrayN(val length: Int, val elementType: IR.Leaf) : IR.Leaf {
   val elementSize: Int get() = when (elementType) {
      is Struct -> elementType.encodedLength
      else -> throw UnsupportedOperationException("ArrayN only supports struct elements for now, got ${elementType::class.simpleName}")
   }
   val structType: Struct get() = elementType as Struct

   override val sbeName = "ByteArray${length * elementSize}"
   override val name = "Array"
   override val defaultValue = "Array($length) { new${structType.name}() }"
   override val randomValue = "Array($length) { random${structType.name}() }"
   override val size = length * elementSize
   override val packageStr = ""
   override val imports = elementType.imports + "sbe4kt.lib.ArrayFW"
}
