package sbe4kt.ir

internal sealed interface IR {
   val sbeName: String
   val kotlinType: String
   val defaultValue: String
   val size: Int

   data object Int32 : IR {
      override val sbeName = "int32";
      override val kotlinType = "Int";
      override val defaultValue = "0";
      override val size = 4
   }

   data object Int64 : IR {
      override val sbeName = "int64";
      override val kotlinType = "Long";
      override val defaultValue = "0L";
      override val size = 8
   }

   data object Int16 : IR {
      override val sbeName = "int16";
      override val kotlinType = "Short";
      override val defaultValue = "0.toShort()";
      override val size = 2
   }

   data object Int8 : IR {
      override val sbeName = "int8";
      override val kotlinType = "Byte";
      override val defaultValue = "0.toByte()";
      override val size = 1
   }

   data object Double : IR {
      override val sbeName = "double";
      override val kotlinType = "Double";
      override val defaultValue = "0.0";
      override val size = 8
   }

   data object Float : IR {
      override val sbeName = "float";
      override val kotlinType = "Float";
      override val defaultValue = "0.0f";
      override val size = 4
   }

   data object Char : IR {
      override val sbeName = "char";
      override val kotlinType = "Char";
      override val defaultValue = "'\\u0000'";
      override val size = 1
   }

   data object Boolean : IR {
      override val sbeName = "uint8";
      override val kotlinType = "Boolean";
      override val defaultValue = "false";
      override val size = 1
   }
}

internal fun String.toSbeType(): IR {
   return when (this) {
      "kotlin.Boolean" -> IR.Boolean
      "kotlin.Char" -> IR.Char
      "kotlin.Byte" -> IR.Int8
      "kotlin.Short" -> IR.Int16
      "kotlin.Int" -> IR.Int32
      "kotlin.Long" -> IR.Int64
      "kotlin.Float" -> IR.Float
      "kotlin.Double" -> IR.Double
      else -> throw IllegalArgumentException("Unsupported type: $this")
   }
}