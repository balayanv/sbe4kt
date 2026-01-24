package sbe4kt.kt

import sbe4kt.ir.Composite
import sbe4kt.ir.Message
import sbe4kt.ir.Union
import sbe4kt.util.indent
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateFlyweightsSection(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()

   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }
   val fieldsSbeOrder = message.fields.sortedBy { it.sbeOrder }
   val compositFields = fieldsDecOrder.filter { it.type is Composite }
   val nestedMessageFields = fieldsDecOrder.filter { it.type is Message }

   // Union fields don't have flyweights - they need direct encode/decode
   val fieldsForFlyweights = fieldsDecOrder.filter { it.type !is Union }

   val fwEncoderBody = fieldsSbeOrder.joinToString("\n") {
      val encoderParam = if (it.type is Message) "${className.replaceFirstChar { it.lowercase() }}Encoder" else "encoder"
      it.type.howToEncodeFromSource(encoderParam, it.name, "source")
   }
   val fwDecoderBody = fieldsSbeOrder.joinToString("\n") {
      val decoderParam = if (it.type is Message) "${className.replaceFirstChar { it.lowercase() }}Decoder" else "decoder"
      it.type.howToDecodeIntoTarget(decoderParam, it.name, "target")
   }

   val fwSetters = fieldsForFlyweights.joinToString("\n") { "fun ${it.name}(${it.name}: ${it.type.name}) = apply { ${it.type.fwSetter("encoder", it.name)} }" }
   val fwGetters = fieldsForFlyweights.joinToString("\n") { "fun ${it.name}() = ${it.type.fwGetter("decoder", it.name)}" }

   // if type uses other complex types expose their generated flyweights
   val fwFieldFWs = compositFields.joinToString("\n") { "private val ${it.name}FW = ${it.type.name}RW(encoder.${it.name}(), decoder.${it.name}())" }
   val fwFieldFwEncoderWraps = compositFields.joinToString("\n") { "encoder.${it.name}()" }
   val fwFieldFwDecoderWraps = compositFields.joinToString("\n") { "decoder.${it.name}()" }
   val fwFieldFwGettersRW = compositFields.joinToString("\n") { "fun ${it.name}FW() = ${it.name}FW" }

   // For R (read-only) flyweights
   val fwFieldFWsR = compositFields.joinToString("\n") { "private val ${it.name}FW = ${it.type.name}R(decoder.${it.name}())" }
   val fwFieldFwGettersR = compositFields.joinToString("\n") { "fun ${it.name}FW() = ${it.name}FW" }

   // For W (write-only) flyweights
   val fwFieldFWsW = compositFields.joinToString("\n") { "private val ${it.name}FW = ${it.type.name}W(encoder.${it.name}())" }
   val fwFieldFwGettersW = compositFields.joinToString("\n") { "fun ${it.name}FW() = ${it.name}FW" }

   // For nested @SBE messages, create flyweight wrappers using buffer positions
   val nestedMessageFWsRW = nestedMessageFields.joinToString("\n") {
      "private val ${it.name}FW = ${it.type.name}RW(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Encoder.${it.name}EncodingOffset())"
   }
   val nestedMessageFWsR = nestedMessageFields.joinToString("\n") {
      "private val ${it.name}FW = ${it.type.name}R(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Encoder.${it.name}EncodingOffset())"
   }
   val nestedMessageFWsW = nestedMessageFields.joinToString("\n") {
      "private val ${it.name}FW = ${it.type.name}W(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Encoder.${it.name}EncodingOffset())"
   }
   val nestedMessageFwGetters = nestedMessageFields.joinToString("\n") { "fun ${it.name}FW() = ${it.name}FW" }

   return """
      |class ${className}RW(
      |   var buffer: MutableDirectBuffer = UnsafeBuffer(ByteArray($sizeConstName)),
      |   var offset: Int = 0
      |) {
      |   private val headerEncoder = MessageHeaderEncoder()
      |   private val encoder = ${className}Encoder()
      |
      |   private val headerDecoder = MessageHeaderDecoder()
      |   private val decoder = ${className}Decoder()
      |
      |${fwFieldFWs.indent(3)}
      |
      |${nestedMessageFWsRW.indent(3)}
      |
      |   init {
      |      prepareBuffer()
      |   }
      |
      |   fun blockLength() = headerDecoder.blockLength()
      |   fun templateId() = headerDecoder.templateId()
      |   fun schemaId() = headerDecoder.schemaId()
      |   fun version() = headerDecoder.version()
      |
      |   private fun prepareBuffer() {
      |      check(buffer.capacity() - offset >= $sizeConstName) { "buffer has not enough capacity for '$className' (need at least : $$sizeConstName, actual : ${'$'}{buffer.capacity()})" }
      |      encoder.wrapAndApplyHeader(buffer, offset, headerEncoder)
      |${fwFieldFwEncoderWraps.indent(6)}
      |      decoder.wrapAndApplyHeader(buffer, offset, headerDecoder)
      |${fwFieldFwDecoderWraps.indent(6)}
      |   }
      |
      |   fun wrap(buffer: MutableDirectBuffer, offset: Int) = apply {
      |      this.buffer = buffer
      |      this.offset = offset
      |      prepareBuffer()
      |   }
      |
      |
      |${fwFieldFwGettersRW.indent(3)}
      |
      |${nestedMessageFwGetters.indent(3)}
      |
      |${fwGetters.indent(3)}
      |
      |${fwSetters.indent(3)}
      |
      |   fun encodeFrom(source: $className) = apply {
      |${fwEncoderBody.indent(6)}
      |   }
      |
      |   fun decodeInto(target: $className = new$className()) = apply {
      |${fwDecoderBody.indent(6)}
      |   }
      |}
      |
      |class ${className}R(
      |   var buffer: DirectBuffer = UnsafeBuffer(ByteArray($sizeConstName)),
      |   var offset: Int = 0
      |) {
      |   private val headerDecoder = MessageHeaderDecoder()
      |   private val decoder = ${className}Decoder()
      |
      |${fwFieldFWsR.indent(3)}
      |
      |${nestedMessageFWsR.indent(3)}
      |
      |   init {
      |      prepareBuffer()
      |   }
      |
      |   fun blockLength() = headerDecoder.blockLength()
      |   fun templateId() = headerDecoder.templateId()
      |   fun schemaId() = headerDecoder.schemaId()
      |   fun version() = headerDecoder.version()
      |
      |   private fun prepareBuffer() {
      |      check(buffer.capacity() - offset >= $sizeConstName) { "buffer has not enough capacity for '$className' (need at least : $$sizeConstName, actual : ${'$'}{buffer.capacity()})" }
      |      decoder.wrapAndApplyHeader(buffer, offset, headerDecoder)
      |   }
      |
      |   fun wrap(buffer: DirectBuffer, offset: Int) = apply {
      |      this.buffer = buffer
      |      this.offset = offset
      |      prepareBuffer()
      |   }
      |
      |
      |${fwFieldFwGettersR.indent(3)}
      |
      |${nestedMessageFwGetters.indent(3)}
      |
      |${fwGetters.indent(3)}
      |
      |   fun decodeInto(target: $className = new$className()) = apply {
      |${fwDecoderBody.indent(6)}
      |   }
      |}
      |
      |
      |class ${className}W(
      |   var buffer: MutableDirectBuffer = UnsafeBuffer(ByteArray($sizeConstName)),
      |   var offset: Int = 0
      |) {
      |   private val headerEncoder = MessageHeaderEncoder()
      |   private val encoder = ${className}Encoder()
      |
      |${fwFieldFWsW.indent(3)}
      |
      |${nestedMessageFWsW.indent(3)}
      |
      |   init {
      |      prepareBuffer()
      |   }
      |
      |   fun blockLength() = ${className}Encoder.BLOCK_LENGTH
      |   fun templateId() = ${className}Encoder.TEMPLATE_ID
      |   fun schemaId() = ${className}Encoder.SCHEMA_ID
      |   fun version() = ${className}Encoder.SCHEMA_VERSION
      |
      |   private fun prepareBuffer() {
      |      check(buffer.capacity() - offset >= $sizeConstName) { "buffer has not enough capacity for '$className' (need at least : $$sizeConstName, actual : ${'$'}{buffer.capacity()})" }
      |      encoder.wrapAndApplyHeader(buffer, offset, headerEncoder)
      |   }
      |
      |   fun wrap(buffer: MutableDirectBuffer, offset: Int) = apply {
      |      this.buffer = buffer
      |      this.offset = offset
      |      prepareBuffer()
      |   }
      |
      |
      |${fwFieldFwGettersW.indent(3)}
      |
      |${nestedMessageFwGetters.indent(3)}
      |
      |${fwSetters.indent(3)}
      |
      |   fun encodeFrom(source: $className) = apply {
      |${fwEncoderBody.indent(6)}
      |   }
      |}
      |""".trimTemplate()
}