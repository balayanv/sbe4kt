package sbe4kt.kt

import sbe4kt.ir.Composite
import sbe4kt.ir.Field
import sbe4kt.ir.Message
import sbe4kt.ir.Union
import sbe4kt.util.indent
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateFlyweightsSection(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()
   val encoderName = className.replaceFirstChar { it.lowercase() } + "Encoder"
   val decoderName = className.replaceFirstChar { it.lowercase() } + "Decoder"
   val headerEncoderName = className.replaceFirstChar { it.lowercase() } + "HeaderEncoder"
   val headerDecoderName = className.replaceFirstChar { it.lowercase() } + "HeaderDecoder"

   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }
   val compositFields = fieldsDecOrder.filter { it.type is Composite }
   val nestedMessageFields = fieldsDecOrder.filter { it.type is Message }
   val unionFields = fieldsDecOrder.filter { it.type is Union }

   val fieldsForFlyweights = fieldsDecOrder.filter { it.type !is Union && it.type !is Composite && it.type !is Message }

   val fwSetters = fieldsForFlyweights.joinToString("\n") { "fun ${it.name}(${it.name}: ${it.type.name}) = apply { ${it.type.fwSetter(encoderName, it.name)} }" }
   val fwGetters = fieldsForFlyweights.joinToString("\n") { "fun ${it.name}() = ${it.type.fwGetter(decoderName, it.name)}" }

   // composite flyweights created directly from parent encoder/decoder
   val compositeFWs = compositFields.joinToString("\n") { "private val ${it.name}FW = ${it.type.name}RW($encoderName.${it.name}(), $decoderName.${it.name}())" }
   val compositeEncoderWraps = compositFields.joinToString("\n") { "${it.name}FW.encoder.wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Encoder.${it.name}EncodingOffset())" }
   val compositeDecoderWraps = compositFields.joinToString("\n") { "${it.name}FW.decoder.wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Decoder.${it.name}EncodingOffset())" }
   val compositeFwGetters = compositFields.joinToString("\n") { "fun ${it.name}() = ${it.name}FW" }

   // composite encoder/decoder args for encode/decode calls (accessed via FW)
   val compositeEncodeArgs = compositFields.joinToString("") { ", ${it.name}FW.encoder" }
   val compositeDecodeArgs = compositFields.joinToString("") { ", ${it.name}FW.decoder" }

   // union fields — shared header encoder/decoder + pre-created variant FWs
   val unionConstructorParams = generateUnionConstructorParams(unionFields)
   val unionPrepareBufferWraps = generateUnionPrepareBufferWraps(unionFields)
   val unionOffsetFns = generateUnionOffsetFns(unionFields, className)
   val unionEnums = generateUnionEnums(unionFields)
   val unionGetters = generateUnionGetters(unionFields)
   val unionSetters = generateUnionSetters(unionFields)

   // union encoder/decoder args for encode/decode calls (accessed via FWs)
   val unionEncodeArgs = unionFields.joinToString("") { field ->
      val union = field.type as Union
      val args = mutableListOf(", ${field.name}HeaderEncoder")
      for (type in union.allowedTypes) {
         val variantLower = type.name.replaceFirstChar { it.lowercase() }
         args.add(", ${variantLower}FW.${variantLower}Encoder")
      }
      args.joinToString("")
   }
   val unionDecodeArgs = unionFields.joinToString("") { field ->
      val union = field.type as Union
      val args = mutableListOf(", ${field.name}HeaderDecoder")
      for (type in union.allowedTypes) {
         val variantLower = type.name.replaceFirstChar { it.lowercase() }
         args.add(", ${variantLower}FW.${variantLower}Decoder")
      }
      args.joinToString("")
   }

   // nested message flyweights — constructed with (buffer), re-wrapped in prepareBuffer
   val nestedMessageFWs = nestedMessageFields.joinToString("\n") { "private val ${it.name}FW = ${it.type.name}RW(buffer)" }
   val nestedMessageOffsetFns = nestedMessageFields.joinToString("\n") { "private fun ${it.name}Offset() = offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Encoder.${it.name}EncodingOffset()" }
   val nestedMessageFwWraps = nestedMessageFields.joinToString("\n") { "${it.name}FW.wrap(buffer, ${it.name}Offset())" }
   val nestedMessageFwGetters = nestedMessageFields.joinToString("\n") { "fun ${it.name}() = ${it.name}FW" }

   // nested message encoder/decoder args accessed via FW for encode/decode calls
   val nestedEncodeArgs = nestedMessageFields.joinToString("") {
      val typeLower = it.type.name.replaceFirstChar { c -> c.lowercase() }
      ", ${it.name}FW.${typeLower}Encoder"
   }
   val nestedDecodeArgs = nestedMessageFields.joinToString("") {
      val typeLower = it.type.name.replaceFirstChar { c -> c.lowercase() }
      ", ${it.name}FW.${typeLower}Decoder"
   }

   val constructorParams = listOf(
      "private var buffer: MutableDirectBuffer = UnsafeBuffer(ByteArray($sizeConstName)),",
      "private var offset: Int = 0,",
      "val $headerEncoderName: MessageHeaderEncoder = MessageHeaderEncoder(),",
      "val $headerDecoderName: MessageHeaderDecoder = MessageHeaderDecoder(),",
      "val $encoderName: ${className}Encoder = ${className}Encoder(),",
      "val $decoderName: ${className}Decoder = ${className}Decoder(),",
      unionConstructorParams,
   ).filter { it.isNotEmpty() }.joinToString("\n")

   val classBodyMembers = listOf(
      compositeFWs,
      nestedMessageFWs,
      unionEnums,
   ).filter { it.isNotEmpty() }.joinToString("\n\n")

   val prepareBufferBody = listOf(
      "check(buffer.capacity() - offset >= $sizeConstName) { \"buffer has not enough capacity for '$className' (need at least : $$sizeConstName, actual : ${'$'}{buffer.capacity()})\" }",
      "$encoderName.wrapAndApplyHeader(buffer, offset, $headerEncoderName)",
      "$decoderName.wrapAndApplyHeader(buffer, offset, $headerDecoderName)",
      compositeEncoderWraps,
      compositeDecoderWraps,
      unionPrepareBufferWraps,
      nestedMessageFwWraps,
   ).filter { it.isNotEmpty() }.joinToString("\n")

   val utilityFunctions = listOf(
      unionOffsetFns,
      nestedMessageOffsetFns,
   ).filter { it.isNotEmpty() }.joinToString("\n")

   val getters = listOf(
      compositeFwGetters,
      nestedMessageFwGetters,
      fwGetters,
      unionGetters,
   ).filter { it.isNotEmpty() }.joinToString("\n\n")

   val setters = listOf(
      fwSetters,
      unionSetters,
   ).filter { it.isNotEmpty() }.joinToString("\n")

   return """
      |class ${className}RW(
      |${constructorParams.indent(3)}
      |) {
      |${classBodyMembers.indent(3)}
      |
      |   init {
      |      prepareBuffer()
      |   }
      |
      |   fun blockLength() = $headerDecoderName.blockLength()
      |   fun templateId() = $headerDecoderName.templateId()
      |   fun schemaId() = $headerDecoderName.schemaId()
      |   fun version() = $headerDecoderName.version()
      |
      |   private fun prepareBuffer() {
      |${prepareBufferBody.indent(6)}
      |   }
      |
      |   fun wrap(buffer: MutableDirectBuffer, offset: Int) = apply {
      |      this.buffer = buffer
      |      this.offset = offset
      |      prepareBuffer()
      |   }
      |
      |${utilityFunctions.indent(3)}
      |
      |${getters.indent(3)}
      |
      |${setters.indent(3)}
      |
      |   fun encodeFrom(source: $className) = apply {
      |      encode$className(source, $encoderName$compositeEncodeArgs$nestedEncodeArgs$unionEncodeArgs)
      |   }
      |
      |   fun decodeInto(target: $className) = apply {
      |      decode$className(target, $decoderName$compositeDecodeArgs$nestedDecodeArgs$unionDecodeArgs)
      |   }
      |}
      |""".trimTemplate()
}

/**
 * Generates constructor params for union fields:
 * - shared header encoder/decoder per union field
 * - a pre-created FW for each variant type
 */
private fun generateUnionConstructorParams(unionFields: List<Field>): String {
   if (unionFields.isEmpty()) return ""
   return unionFields.joinToString("\n") { field ->
      val union = field.type as Union
      val lines = mutableListOf<String>()
      lines.add("private val ${field.name}HeaderEncoder: MessageHeaderEncoder = MessageHeaderEncoder(),")
      lines.add("private val ${field.name}HeaderDecoder: MessageHeaderDecoder = MessageHeaderDecoder(),")
      for (type in union.allowedTypes) {
         val variantLower = type.name.replaceFirstChar { it.lowercase() }
         lines.add("private val ${variantLower}FW: ${type.name}RW = ${type.name}RW(buffer, 0, ${field.name}HeaderEncoder, ${field.name}HeaderDecoder),")
      }
      lines.joinToString("\n")
   }
}

/**
 * Generates prepareBuffer wraps for union header encoder/decoder.
 */
private fun generateUnionPrepareBufferWraps(unionFields: List<Field>): String {
   if (unionFields.isEmpty()) return ""
   return unionFields.joinToString("\n") { field ->
      "${field.name}HeaderEncoder.wrap(buffer, ${field.name}Offset())\n${field.name}HeaderDecoder.wrap(buffer, ${field.name}Offset())"
   }
}

/**
 * Generates private offset methods for union fields.
 */
private fun generateUnionOffsetFns(unionFields: List<Field>, className: String): String {
   if (unionFields.isEmpty()) return ""
   return unionFields.joinToString("\n") { field ->
      "private fun ${field.name}Offset() = offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Encoder.${field.name}EncodingOffset()"
   }
}

/**
 * Generates enum classes for union fields, e.g.:
 * enum class PayloadType(val templateId: Int) {
 *    FAILURE(FailureEncoder.TEMPLATE_ID),
 *    SUCCESS(SuccessEncoder.TEMPLATE_ID),
 * }
 */
private fun generateUnionEnums(unionFields: List<Field>): String {
   if (unionFields.isEmpty()) return ""
   return unionFields.joinToString("\n\n") { field ->
      val union = field.type as Union
      val enumName = "${field.name.replaceFirstChar { it.uppercase() }}Type"
      val entries = union.allowedTypes.joinToString("\n") { type ->
         "${type.name.toUpperSnakeCase()}(${type.name}Encoder.TEMPLATE_ID),"
      }
      "enum class $enumName(val templateId: Int) {\n${entries.prependIndent("   ")}\n}"
   }
}

/**
 * Generates union getters:
 * - payloadTemplateId(): Int — raw templateId from the shared header decoder
 * - payloadType(): PayloadType — enum-based type accessor
 * - payload(): Any — convenience returning the appropriate FW
 * - failure(): FailureRW, success(): SuccessRW — per-variant getters
 */
private fun generateUnionGetters(unionFields: List<Field>): String {
   if (unionFields.isEmpty()) return ""
   return unionFields.joinToString("\n") { field ->
      val union = field.type as Union
      val enumName = "${field.name.replaceFirstChar { it.uppercase() }}Type"
      val lines = mutableListOf<String>()

      // raw templateId accessor
      lines.add("fun ${field.name}TemplateId() = ${field.name}HeaderDecoder.templateId()")

      // enum-based type accessor
      lines.add("fun ${field.name}Type(): $enumName {")
      lines.add("   return when (${field.name}TemplateId()) {")
      for (type in union.allowedTypes) {
         lines.add("      ${type.name}Encoder.TEMPLATE_ID -> $enumName.${type.name.toUpperSnakeCase()}")
      }
      lines.add("      else -> throw IllegalArgumentException(\"Unknown templateId in union field '${field.name}': \${${field.name}TemplateId()}\")")
      lines.add("   }")
      lines.add("}")

      // convenience payload() returning Any
      lines.add("fun ${field.name}(): Any = when (${field.name}Type()) {")
      for (type in union.allowedTypes) {
         val variantUpper = type.name.replaceFirstChar { it.uppercase() }
         lines.add("   $enumName.${type.name.toUpperSnakeCase()} -> ${field.name}$variantUpper()")
      }
      lines.add("}")

      // per-variant getters prefixed with field name
      for (type in union.allowedTypes) {
         val variantLower = type.name.replaceFirstChar { it.lowercase() }
         val variantUpper = type.name.replaceFirstChar { it.uppercase() }
         lines.add("fun ${field.name}$variantUpper(): ${type.name}RW = ${variantLower}FW.wrap(buffer, ${field.name}Offset())")
      }

      lines.joinToString("\n")
   }
}

private fun String.toUpperSnakeCase(): String {
   return replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
}

/**
 * Generates union setters using wrapAndEncode with buffer and offset method.
 */
private fun generateUnionSetters(unionFields: List<Field>): String {
   if (unionFields.isEmpty()) return ""
   return unionFields.joinToString("\n") { field ->
      val union = field.type as Union
      buildString {
         appendLine("fun ${field.name}(${field.name}: ${union.name}) = apply {")
         appendLine("   when (${field.name}) {")
         for (type in union.allowedTypes) {
            appendLine("      is ${type.name} -> wrapAndEncode${type.name}(${field.name}, buffer, ${field.name}Offset())")
         }
         if (union.typeName == "Any") {
            appendLine("      else -> throw IllegalArgumentException(\"Unexpected type in union field '${field.name}': \${${field.name}::class.qualifiedName}\")")
         }
         appendLine("   }")
         append("}")
      }
   }
}
