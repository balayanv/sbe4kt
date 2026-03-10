package sbe4kt.ir

internal data class ArrayN(val length: Int, val elementType: IR.Leaf) : IR.Leaf {
   val isStruct: Boolean get() = elementType is Struct
   val structType: Struct get() = elementType as Struct

   override val size = length * elementType.size

   override val sbeName: String get() = when (elementType) {
      is Int8 -> "ByteArray$length"
      is Char -> "CharArray$length"
      is Struct -> "ByteArray$size"
      else -> "${elementType.name}Array$length"
   }

   override val name: String get() = when (elementType) {
      is Struct -> "Array"
      else -> "${elementType.name}Array"
   }

   override val defaultValue: String get() = when (elementType) {
      is Struct -> "Array($length) { new${structType.name}() }"
      is Char -> "CharArray($length)"
      else -> "${name}($length)"
   }

   override val randomValue: String get() = when (elementType) {
      is Struct -> "Array($length) { random${structType.name}() }"
      is Char -> "CharArray($length) { Random.nextInt(32, 127).toChar() }"
      else -> "${name}($length) { ${elementType.randomValue} }"
   }

   override val packageStr = ""

   override val imports: Set<String> get() = buildSet {
      add("kotlin.random.Random")
      if (isStruct) {
         addAll(elementType.imports)
         add("sbe4kt.lib.ArrayFW")
      } else {
         add("sbe4kt.lib.${elementType.name}ArrayCodec")
         add("sbe4kt.lib.${elementType.name}ArrayFW")
      }
   }

   val codecName: String get() = if (isStruct) "${structType.name}Codec" else "${elementType.name}ArrayCodec"
   val fwName: String get() = if (isStruct) "ArrayFW<${structType.name}FW>" else "${elementType.name}ArrayFW"
}
