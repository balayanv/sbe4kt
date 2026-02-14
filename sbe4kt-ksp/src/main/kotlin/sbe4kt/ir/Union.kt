package sbe4kt.ir

internal data class Union(
   val allowedTypes: List<Message>,
   val fieldName: String,
   val typeName: String = "Any",
   val typePackage: String = "",
) : IR.Leaf {
   val maxSize: Int = allowedTypes.maxOf { it.size }

   override val sbeName = "ByteArray$maxSize"
   override val name = typeName
   override val size: Int = maxSize
   override val packageStr = typePackage
   override val defaultValue = allowedTypes.first().defaultValue
   override val randomValue = allowedTypes.first().randomValue

   override val imports by lazy {
      buildSet {
         for (type in allowedTypes) {
            addAll(type.imports)
         }
         if (typeName != "Any" && typePackage.isNotEmpty()) {
            add("$typePackage.$typeName")
         }
      }
   }

   override fun howToEncodeFromSource(encoder: String, field: String, instance: String): String {
      val encoderClass = encoder.replaceFirstChar { it.uppercase() }
      val fieldOffset = "$encoder.offset() + ${encoderClass}.${field}EncodingOffset()"
      val sb = StringBuilder()
      sb.appendLine("when (val value = $instance.$field) {")
      for (type in allowedTypes) {
         val variantEncoder = "${type.name.replaceFirstChar { it.lowercase() }}Encoder"
         sb.appendLine("   is ${type.name} -> wrapAndEncode${type.name}(value, $encoder.buffer(), $fieldOffset, ${field}HeaderEncoder, $variantEncoder)")
      }
      if (typeName == "Any") {
         sb.appendLine("   else -> throw IllegalArgumentException(\"Unexpected type in union field '$field': \${value::class.qualifiedName}\")")
      }
      sb.appendLine("}")
      return sb.removeSuffix("\n").toString()
   }

   override fun howToDecodeIntoTarget(decoder: String, field: String, instance: String): String {
      val decoderClass = decoder.replaceFirstChar { it.uppercase() }
      val fieldOffset = "$decoder.offset() + ${decoderClass}.${field}EncodingOffset()"
      val sb = StringBuilder()
      sb.appendLine("$instance.$field = when (${field}HeaderDecoder.templateId()) {")
      for (type in allowedTypes) {
         val variantDecoder = "${type.name.replaceFirstChar { it.lowercase() }}Decoder"
         sb.appendLine("   ${type.name}Encoder.TEMPLATE_ID -> new${type.name}().also { wrapAndDecode${type.name}(it, $decoder.buffer(), $fieldOffset, ${field}HeaderDecoder, $variantDecoder) }")
      }
      sb.appendLine("   else -> throw IllegalArgumentException(\"Unknown templateId in union field '$field': \${${field}HeaderDecoder.templateId()}\")")
      sb.appendLine("}")
      return sb.removeSuffix("\n").toString()
   }

   override fun fwSetter(encoder: String, field: String): String {
      throw UnsupportedOperationException("Union fwSetter is handled by the flyweight generator")
   }

   override fun fwGetter(decoder: String, field: String): String {
      throw UnsupportedOperationException("Union fwGetter is handled by the flyweight generator")
   }
}
