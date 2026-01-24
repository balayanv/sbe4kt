package sbe4kt.ir

import sbe4kt.util.id

internal data class Union(
   val allowedTypes: List<IR.Leaf>,
   val fieldName: String,
) : IR.Leaf {
   val maxSize: Int = allowedTypes.maxOf { it.size }

   // Assign discriminators for union types
   // Use explicit IDs from @SBE(id=...) when available, otherwise use index
   private val typeDiscriminators = allowedTypes.mapIndexed { index, type ->
      type to when (type) {
         is Message -> type.id.toString()
         is Struct -> {
            // If struct has an explicit ID from @SBE(id=...), use it
            val explicitId = type.annotation.id()
            if (explicitId > 0) {
               explicitId.toString()
            } else {
               index.toString()
            }
         }
         else -> throw IllegalArgumentException("Union can only contain Message or Struct types, got ${type::class.simpleName}")
      }
   }.toMap()

   override val sbeName = "${fieldName}Union"
   override val name = "Any"
   override val size: Int = 4 + maxSize
   override val packageStr = ""
   override val defaultValue = allowedTypes.first().defaultValue
   override val randomValue = allowedTypes.first().randomValue

   override val imports by lazy {
      buildSet {
         for (type in allowedTypes) {
            addAll(type.imports)
         }
      }
   }

   override fun howToEncodeFromSource(encoder: String, field: String, instance: String): String {
      val sb = StringBuilder()
      sb.appendLine("val $field = $encoder.$field()")
      sb.appendLine("when (val value = $instance.$field) {")
      for (type in allowedTypes) {
         val typeName = type.name
         val discriminator = typeDiscriminators[type]!!
         val encoderClass = "${typeName}Encoder"
         sb.appendLine("   is $typeName -> {")
         sb.appendLine("      $field.type($discriminator)")
         when (type) {
            is Message -> {
               sb.appendLine("      encode$typeName(value, $field.buffer(), $field.offset() + ${baseName(type)}Encoder.dataEncodingOffset())")
            }
            is Struct -> {
               // For composites, directly write to the buffer using the data offset
               val dataOffset = "$field.offset() + ${baseName(type)}Encoder.dataEncodingOffset()"
               var currentOffset = 0
               type.fields.sortedBy { it.sbeOrder }.forEach { structField ->
                  val encodedValue = structField.type.convertToSbe("value.${structField.name}")
                  val putMethod = when (structField.type.sbeName) {
                     "int64", "uint64" -> "putLong"
                     "int32", "uint32" -> "putInt"
                     "int16", "uint16" -> "putShort"
                     "int8", "uint8" -> "putByte"
                     "float" -> "putFloat"
                     "double" -> "putDouble"
                     else -> throw IllegalArgumentException("Unsupported composite field type: ${structField.type.sbeName}")
                  }
                  sb.appendLine("      $field.buffer().$putMethod($dataOffset + $currentOffset, $encodedValue, java.nio.ByteOrder.LITTLE_ENDIAN)")
                  currentOffset += structField.type.size
               }
            }
            else -> throw IllegalArgumentException("Unsupported union type: ${type::class.simpleName}")
         }
         sb.appendLine("   }")
      }
      sb.appendLine("   else -> throw IllegalArgumentException(\"Unexpected type in union field '$field': \${value::class.qualifiedName}\")")
      sb.appendLine("}")
      return sb.removeSuffix("\n").toString()
   }

   override fun howToDecodeIntoTarget(decoder: String, field: String, instance: String): String {
      val sb = StringBuilder()
      sb.appendLine("val $field = $decoder.$field()")
      sb.appendLine("$instance.$field = when ($field.type()) {")
      for (type in allowedTypes) {
         val typeName = type.name
         val discriminator = typeDiscriminators[type]!!
         when (type) {
            is Message -> {
               sb.appendLine("   $discriminator -> new$typeName().also { decode$typeName(it, $field.buffer(), $field.offset() + ${baseName(type)}Decoder.dataEncodingOffset()) }")
            }
            is Struct -> {
               sb.appendLine("   $discriminator -> {")
               // For composites, directly read from the buffer using the data offset
               val dataOffset = "$field.offset() + ${baseName(type)}Decoder.dataEncodingOffset()"
               var currentOffset = 0
               val fieldReads = type.fields.sortedBy { it.sbeOrder }.map { structField ->
                  val getMethod = when (structField.type.sbeName) {
                     "int64" -> "getLong"
                     "uint64" -> "getLong"  // Kotlin doesn't have unsigned, use Long
                     "int32" -> "getInt"
                     "uint32" -> "getInt"
                     "int16" -> "getShort"
                     "uint16" -> "getShort"
                     "int8" -> "getByte"
                     "uint8" -> "getByte"
                     "float" -> "getFloat"
                     "double" -> "getDouble"
                     else -> throw IllegalArgumentException("Unsupported composite field type: ${structField.type.sbeName}")
                  }
                  val rawValue = "$field.buffer().$getMethod($dataOffset + $currentOffset, java.nio.ByteOrder.LITTLE_ENDIAN)"
                  val decodedValue = structField.type.convertFromSbe(rawValue)
                  currentOffset += structField.type.size
                  "${structField.name} = $decodedValue"
               }.joinToString(", ")
               sb.appendLine("      $typeName($fieldReads)")
               sb.appendLine("   }")
            }
            else -> throw IllegalArgumentException("Unsupported union type: ${type::class.simpleName}")
         }
      }
      sb.appendLine("   else -> throw IllegalArgumentException(\"Unknown type tag in union field '$field': \${$field.type()}\")")
      sb.appendLine("}")
      return sb.removeSuffix("\n").toString()
   }

   private fun baseName(type: IR.Leaf): String {
      return sbeName.replaceFirstChar { it.uppercase() }
   }

   override fun fwSetter(encoder: String, field: String): String {
      val sb = StringBuilder()
      sb.appendLine("when ($field) {")
      for (type in allowedTypes) {
         val typeName = type.name
         val encoderClass = "${baseName(type)}Encoder"
         sb.appendLine("   is $typeName -> {")
         sb.appendLine("      val unionField = encoder.$field()")
         sb.appendLine("      unionField.type(${typeName}Encoder.TEMPLATE_ID)")
         sb.appendLine("      encode$typeName($field, unionField.buffer(), unionField.offset() + $encoderClass.dataEncodingOffset())")
         sb.appendLine("   }")
      }
      sb.appendLine("   else -> throw IllegalArgumentException(\"Unexpected type in union field '$field': \${$field::class.qualifiedName}\")")
      sb.appendLine("}")
      return sb.removeSuffix("\n").toString()
   }

   override fun fwGetter(decoder: String, field: String): String {
      val sb = StringBuilder()
      sb.appendLine("when (${decoder}.$field().type()) {")
      for (type in allowedTypes) {
         val typeName = type.name
         val decoderClass = "${baseName(type)}Decoder"
         sb.appendLine("   ${typeName}Encoder.TEMPLATE_ID -> {")
         sb.appendLine("      val unionField = ${decoder}.$field()")
         sb.appendLine("      new$typeName().also { decode$typeName(it, unionField.buffer(), unionField.offset() + $decoderClass.dataEncodingOffset()) }")
         sb.appendLine("   }")
      }
      sb.appendLine("   else -> throw IllegalArgumentException(\"Unknown type tag in union field '$field': \${${decoder}.$field().type()}\")")
      sb.appendLine("}")
      return sb.removeSuffix("\n").toString()
   }
}
