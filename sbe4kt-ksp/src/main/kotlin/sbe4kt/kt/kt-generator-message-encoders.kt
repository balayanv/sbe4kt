package sbe4kt.kt

import sbe4kt.ir.Composite
import sbe4kt.ir.Message
import sbe4kt.ir.Union
import sbe4kt.util.indent
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateEncodersSection(message: Message): String {
   val className = message.name
   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }
   val fieldsSbeOrder = message.fields.sortedBy { it.sbeOrder }

   val encoderName = className.replaceFirstChar { it.lowercase() } + "Encoder"
   val decoderName = className.replaceFirstChar { it.lowercase() } + "Decoder"
   val headerEncoderName = className.replaceFirstChar { it.lowercase() } + "HeaderEncoder"
   val headerDecoderName = className.replaceFirstChar { it.lowercase() } + "HeaderDecoder"
   val sourceVarName = "source"
   val targetVarName = "target"

   val sizeConstName = className.sbeSizeConstant()
   val totalSize = message.blockLength

   val defaultParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.defaultValue}," }
   val randomParams = fieldsDecOrder.joinToString("\n") { "${it.name}: ${it.type.name} = ${it.type.randomValue}," }
   val args = fieldsDecOrder.joinToString(", ") { it.name }

   val encoderBody = fieldsSbeOrder.joinToString("\n") { it.type.howToEncodeFromSource(encoderName, it.name, "source") }
   val decoderBody = fieldsSbeOrder.joinToString("\n") { it.type.howToDecodeIntoTarget(decoderName, it.name, "target") }

   // composite field encoder/decoder params
   val compositFields = fieldsDecOrder.filter { it.type is Composite }
   val compositeEncoderParamsWithDefaults = compositFields.joinToString("\n") {
      "${it.name}Encoder: ${it.type.name}Encoder = $encoderName.${it.name}(),"
   }
   val compositeDecoderParamsWithDefaults = compositFields.joinToString("\n") {
      "${it.name}Decoder: ${it.type.name}Decoder = $decoderName.${it.name}(),"
   }
   val compositeEncoderParamsNoDefaults = compositFields.joinToString("\n") {
      "${it.name}Encoder: ${it.type.name}Encoder,"
   }
   val compositeDecoderParamsNoDefaults = compositFields.joinToString("\n") {
      "${it.name}Decoder: ${it.type.name}Decoder,"
   }
   val compositeEncoderWraps = compositFields.joinToString("\n") {
      "${it.name}Encoder.wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Encoder.${it.name}EncodingOffset())"
   }
   val compositeDecoderWraps = compositFields.joinToString("\n") {
      "${it.name}Decoder.wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Decoder.${it.name}EncodingOffset())"
   }
   val compositeEncodeArgs = compositFields.joinToString("") { ", ${it.name}Encoder" }
   val compositeDecodeArgs = compositFields.joinToString("") { ", ${it.name}Decoder" }

   // nested message field encoder/decoder params
   val nestedMessageFields = fieldsDecOrder.filter { it.type is Message }
   val nestedEncoderParamsWithDefaults = nestedMessageFields.joinToString("\n") {
      "${it.name}HeaderEncoder: MessageHeaderEncoder = MessageHeaderEncoder(),\n${it.name}Encoder: ${it.type.name}Encoder = ${it.type.name}Encoder(),"
   }
   val nestedDecoderParamsWithDefaults = nestedMessageFields.joinToString("\n") {
      "${it.name}HeaderDecoder: MessageHeaderDecoder = MessageHeaderDecoder(),\n${it.name}Decoder: ${it.type.name}Decoder = ${it.type.name}Decoder(),"
   }
   val nestedEncoderParamsNoDefaults = nestedMessageFields.joinToString("\n") {
      "${it.name}Encoder: ${it.type.name}Encoder,"
   }
   val nestedDecoderParamsNoDefaults = nestedMessageFields.joinToString("\n") {
      "${it.name}Decoder: ${it.type.name}Decoder,"
   }
   val nestedEncoderWraps = nestedMessageFields.joinToString("\n") {
      "${it.name}Encoder.wrapAndApplyHeader(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Encoder.${it.name}EncodingOffset(), ${it.name}HeaderEncoder)"
   }
   val nestedDecoderWraps = nestedMessageFields.joinToString("\n") {
      "${it.name}Decoder.wrapAndApplyHeader(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Decoder.${it.name}EncodingOffset(), ${it.name}HeaderDecoder)"
   }
   val nestedEncodeArgs = nestedMessageFields.joinToString("") { ", ${it.name}Encoder" }
   val nestedDecodeArgs = nestedMessageFields.joinToString("") { ", ${it.name}Decoder" }

   // union field encoder/decoder params
   val unionFields = fieldsDecOrder.filter { it.type is Union }
   val unionEncoderParamsWithDefaults = unionFields.joinToString("\n") { field ->
      val union = field.type as Union
      val lines = mutableListOf("${field.name}HeaderEncoder: MessageHeaderEncoder = MessageHeaderEncoder(),")
      for (type in union.allowedTypes) {
         val variantLower = type.name.replaceFirstChar { it.lowercase() }
         lines.add("${variantLower}Encoder: ${type.name}Encoder = ${type.name}Encoder(),")
      }
      lines.joinToString("\n")
   }
   val unionDecoderParamsWithDefaults = unionFields.joinToString("\n") { field ->
      val union = field.type as Union
      val lines = mutableListOf("${field.name}HeaderDecoder: MessageHeaderDecoder = MessageHeaderDecoder(),")
      for (type in union.allowedTypes) {
         val variantLower = type.name.replaceFirstChar { it.lowercase() }
         lines.add("${variantLower}Decoder: ${type.name}Decoder = ${type.name}Decoder(),")
      }
      lines.joinToString("\n")
   }
   val unionEncoderParamsNoDefaults = unionFields.joinToString("\n") { field ->
      val union = field.type as Union
      val lines = mutableListOf("${field.name}HeaderEncoder: MessageHeaderEncoder,")
      for (type in union.allowedTypes) {
         val variantLower = type.name.replaceFirstChar { it.lowercase() }
         lines.add("${variantLower}Encoder: ${type.name}Encoder,")
      }
      lines.joinToString("\n")
   }
   val unionDecoderParamsNoDefaults = unionFields.joinToString("\n") { field ->
      val union = field.type as Union
      val lines = mutableListOf("${field.name}HeaderDecoder: MessageHeaderDecoder,")
      for (type in union.allowedTypes) {
         val variantLower = type.name.replaceFirstChar { it.lowercase() }
         lines.add("${variantLower}Decoder: ${type.name}Decoder,")
      }
      lines.joinToString("\n")
   }
   val unionEncodeArgs = unionFields.joinToString("") { field ->
      val union = field.type as Union
      val args = mutableListOf(", ${field.name}HeaderEncoder")
      for (type in union.allowedTypes) {
         val variantLower = type.name.replaceFirstChar { it.lowercase() }
         args.add(", ${variantLower}Encoder")
      }
      args.joinToString("")
   }
   val unionDecodeArgs = unionFields.joinToString("") { field ->
      val union = field.type as Union
      val args = mutableListOf(", ${field.name}HeaderDecoder")
      for (type in union.allowedTypes) {
         val variantLower = type.name.replaceFirstChar { it.lowercase() }
         args.add(", ${variantLower}Decoder")
      }
      args.joinToString("")
   }
   val unionDecoderWraps = unionFields.joinToString("\n") { field ->
      "${field.name}HeaderDecoder.wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Decoder.${field.name}EncodingOffset())"
   }

   // build param/body sections, filtering out empty parts
   val wrapEncodeParams = listOf(
      "$sourceVarName: $className,",
      "buffer: MutableDirectBuffer,",
      "offset: Int = 0,",
      "$headerEncoderName: MessageHeaderEncoder = MessageHeaderEncoder(),",
      "$encoderName: ${className}Encoder = ${className}Encoder(),",
      compositeEncoderParamsWithDefaults,
      nestedEncoderParamsWithDefaults,
      unionEncoderParamsWithDefaults,
   ).filter { it.isNotEmpty() }.joinToString("\n")

   val wrapEncodeBody = listOf(
      "$encoderName.wrapAndApplyHeader(buffer, offset, $headerEncoderName)",
      compositeEncoderWraps,
      nestedEncoderWraps,
      "return encode$className($sourceVarName, $encoderName$compositeEncodeArgs$nestedEncodeArgs$unionEncodeArgs)",
   ).filter { it.isNotEmpty() }.joinToString("\n")

   val encodeParams = listOf(
      "$sourceVarName: $className,",
      "$encoderName: ${className}Encoder,",
      compositeEncoderParamsNoDefaults,
      nestedEncoderParamsNoDefaults,
      unionEncoderParamsNoDefaults,
   ).filter { it.isNotEmpty() }.joinToString("\n")

   val wrapDecodeParams = listOf(
      "$targetVarName: $className,",
      "buffer: DirectBuffer,",
      "offset: Int = 0,",
      "$headerDecoderName: MessageHeaderDecoder = MessageHeaderDecoder(),",
      "$decoderName: ${className}Decoder = ${className}Decoder(),",
      compositeDecoderParamsWithDefaults,
      nestedDecoderParamsWithDefaults,
      unionDecoderParamsWithDefaults,
   ).filter { it.isNotEmpty() }.joinToString("\n")

   val wrapDecodeBody = listOf(
      "$decoderName.wrapAndApplyHeader(buffer, offset, $headerDecoderName)",
      compositeDecoderWraps,
      nestedDecoderWraps,
      unionDecoderWraps,
      "return decode$className($targetVarName, $decoderName$compositeDecodeArgs$nestedDecodeArgs$unionDecodeArgs)",
   ).filter { it.isNotEmpty() }.joinToString("\n")

   val decodeParams = listOf(
      "$targetVarName: $className,",
      "$decoderName: ${className}Decoder,",
      compositeDecoderParamsNoDefaults,
      nestedDecoderParamsNoDefaults,
      unionDecoderParamsNoDefaults,
   ).filter { it.isNotEmpty() }.joinToString("\n")

   return """
      |const val $sizeConstName = MessageHeaderEncoder.ENCODED_LENGTH + $totalSize
      |
      |fun new$className(
      |${defaultParams.indent(3)}
      |): $className {
      |   return $className($args)
      |}
      |
      |fun random$className(
      |${randomParams.indent(3)}
      |): $className {
      |   return $className($args)
      |}
      |
      |fun wrapAndEncode$className(
      |${wrapEncodeParams.indent(3)}
      |){
      |${wrapEncodeBody.indent(3)}
      |}
      |
      |fun encode$className(
      |${encodeParams.indent(3)}
      |){
      |${encoderBody.indent(3)}
      |}
      |
      |fun encodedLength($encoderName: ${className}Encoder) = MessageHeaderEncoder.ENCODED_LENGTH + $encoderName.encodedLength()
      |
      |fun wrapAndDecode$className(
      |${wrapDecodeParams.indent(3)}
      |){
      |${wrapDecodeBody.indent(3)}
      |}
      |
      |fun decode$className(
      |${decodeParams.indent(3)}
      |){
      |${decoderBody.indent(3)}
      |}
      |
      |fun decodedLength($decoderName: ${className}Decoder) = MessageHeaderDecoder.ENCODED_LENGTH + $decoderName.encodedLength()
      |""".trimTemplate()
}
