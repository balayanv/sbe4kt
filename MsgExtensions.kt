package example

import example.Payload.Failure
import example.Payload.Success
import kotlin.random.Random
import org.agrona.DirectBuffer
import org.agrona.MutableDirectBuffer
import org.agrona.concurrent.UnsafeBuffer
import sbe4kt.FailureDecoder
import sbe4kt.FailureEncoder
import sbe4kt.MessageHeaderDecoder
import sbe4kt.MessageHeaderEncoder
import sbe4kt.MsgDecoder
import sbe4kt.MsgEncoder
import sbe4kt.SuccessDecoder
import sbe4kt.SuccessEncoder
import sbe4kt.lib.CSV
import sbe4kt.lib.Nanos
import sbe4kt.lib.TimeId
const val SBE_SIZE_MSG = MessageHeaderEncoder.ENCODED_LENGTH + 32

fun newMsg(
   id: TimeId = TimeId(Nanos(0L)),
   payload: Payload = newFailure(),
): Msg {
   return Msg(id, payload)
}

fun randomMsg(
   id: TimeId = TimeId(Nanos(Random.nextLong())),
   payload: Payload = randomFailure(),
): Msg {
   return Msg(id, payload)
}

fun wrapAndEncodeMsg(
   source: Msg,
   buffer: MutableDirectBuffer,
   offset: Int = 0,
   msgHeaderEncoder: MessageHeaderEncoder = MessageHeaderEncoder(),
   msgEncoder: MsgEncoder = MsgEncoder(),
   payloadHeaderEncoder: MessageHeaderEncoder = MessageHeaderEncoder(),
   failureEncoder: FailureEncoder = FailureEncoder(),
   successEncoder: SuccessEncoder = SuccessEncoder(),
){
   msgEncoder.wrapAndApplyHeader(buffer, offset, msgHeaderEncoder)
   return encodeMsg(source, msgEncoder, payloadHeaderEncoder, failureEncoder, successEncoder)
}

fun encodeMsg(
   source: Msg,
   msgEncoder: MsgEncoder,
   payloadHeaderEncoder: MessageHeaderEncoder,
   failureEncoder: FailureEncoder,
   successEncoder: SuccessEncoder,
){
   when (val value = source.payload) {
      is Failure -> wrapAndEncodeFailure(value, msgEncoder.buffer(), msgEncoder.offset() + MsgEncoder.payloadEncodingOffset(), payloadHeaderEncoder, failureEncoder)
      is Success -> wrapAndEncodeSuccess(value, msgEncoder.buffer(), msgEncoder.offset() + MsgEncoder.payloadEncodingOffset(), payloadHeaderEncoder, successEncoder)
   }
   msgEncoder.id(source.id.value.nanos)
}

fun encodedLength(msgEncoder: MsgEncoder) = MessageHeaderEncoder.ENCODED_LENGTH + msgEncoder.encodedLength()

fun wrapAndDecodeMsg(
   target: Msg,
   buffer: DirectBuffer,
   offset: Int = 0,
   msgHeaderDecoder: MessageHeaderDecoder = MessageHeaderDecoder(),
   msgDecoder: MsgDecoder = MsgDecoder(),
   payloadHeaderDecoder: MessageHeaderDecoder = MessageHeaderDecoder(),
   failureDecoder: FailureDecoder = FailureDecoder(),
   successDecoder: SuccessDecoder = SuccessDecoder(),
){
   msgDecoder.wrapAndApplyHeader(buffer, offset, msgHeaderDecoder)
   return decodeMsg(target, msgDecoder, payloadHeaderDecoder, failureDecoder, successDecoder)
}

fun decodeMsg(
   target: Msg,
   msgDecoder: MsgDecoder,
   payloadHeaderDecoder: MessageHeaderDecoder,
   failureDecoder: FailureDecoder,
   successDecoder: SuccessDecoder,
){
   target.payload = when (payloadHeaderDecoder.templateId()) {
      FailureEncoder.TEMPLATE_ID -> newFailure().also { wrapAndDecodeFailure(it, msgDecoder.buffer(), msgDecoder.offset() + MsgDecoder.payloadEncodingOffset(), payloadHeaderDecoder, failureDecoder) }
      SuccessEncoder.TEMPLATE_ID -> newSuccess().also { wrapAndDecodeSuccess(it, msgDecoder.buffer(), msgDecoder.offset() + MsgDecoder.payloadEncodingOffset(), payloadHeaderDecoder, successDecoder) }
      else -> throw IllegalArgumentException("Unknown templateId in union field 'payload': ${payloadHeaderDecoder.templateId()}")
   }
   target.id = TimeId(Nanos(msgDecoder.id()))
}

fun decodedLength(msgDecoder: MsgDecoder) = MessageHeaderDecoder.ENCODED_LENGTH + msgDecoder.encodedLength()

class MsgRW(
   private var buffer: MutableDirectBuffer = UnsafeBuffer(ByteArray(SBE_SIZE_MSG)),
   private var offset: Int = 0,
   val msgHeaderEncoder: MessageHeaderEncoder = MessageHeaderEncoder(),
   val msgHeaderDecoder: MessageHeaderDecoder = MessageHeaderDecoder(),
   val msgEncoder: MsgEncoder = MsgEncoder(),
   val msgDecoder: MsgDecoder = MsgDecoder(),
   private val payloadHeaderEncoder: MessageHeaderEncoder = MessageHeaderEncoder(),
   private val payloadHeaderDecoder: MessageHeaderDecoder = MessageHeaderDecoder(),
   private val failureFW: FailureRW = FailureRW(buffer, 0, payloadHeaderEncoder, payloadHeaderDecoder),
   private val successFW: SuccessRW = SuccessRW(buffer, 0, payloadHeaderEncoder, payloadHeaderDecoder),
) {
   enum class PayloadType(val templateId: Int) {
      FAILURE(FailureEncoder.TEMPLATE_ID),
      SUCCESS(SuccessEncoder.TEMPLATE_ID),
   }

   init {
      prepareBuffer()
   }

   fun blockLength() = msgHeaderDecoder.blockLength()
   fun templateId() = msgHeaderDecoder.templateId()
   fun schemaId() = msgHeaderDecoder.schemaId()
   fun version() = msgHeaderDecoder.version()

   private fun prepareBuffer() {
      check(buffer.capacity() - offset >= SBE_SIZE_MSG) { "buffer has not enough capacity for 'Msg' (need at least : $SBE_SIZE_MSG, actual : ${buffer.capacity()})" }
      msgEncoder.wrapAndApplyHeader(buffer, offset, msgHeaderEncoder)
      msgDecoder.wrapAndApplyHeader(buffer, offset, msgHeaderDecoder)
      payloadHeaderEncoder.wrap(buffer, payloadOffset())
      payloadHeaderDecoder.wrap(buffer, payloadOffset())
   }

   fun wrap(buffer: MutableDirectBuffer, offset: Int) = apply {
      this.buffer = buffer
      this.offset = offset
      prepareBuffer()
   }

   private fun payloadOffset() = offset + MessageHeaderEncoder.ENCODED_LENGTH + MsgEncoder.payloadEncodingOffset()

   fun id() = TimeId(Nanos(msgDecoder.id()))
   
   fun payloadTemplateId() = payloadHeaderDecoder.templateId()
   fun payloadType(): PayloadType {
      return when (payloadTemplateId()) {
         FailureEncoder.TEMPLATE_ID -> PayloadType.FAILURE
         SuccessEncoder.TEMPLATE_ID -> PayloadType.SUCCESS
         else -> throw IllegalArgumentException("Unknown templateId in union field 'payload': ${payloadTemplateId()}")
      }
   }
   fun payload(): Any = when (payloadType()) {
      PayloadType.FAILURE -> payloadFailure()
      PayloadType.SUCCESS -> payloadSuccess()
   }
   fun payloadFailure(): FailureRW = failureFW.wrap(buffer, payloadOffset())
   fun payloadSuccess(): SuccessRW = successFW.wrap(buffer, payloadOffset())

   fun id(id: TimeId) = apply { msgEncoder.id(id.value.nanos) }
   fun payload(payload: Payload) = apply {
      when (payload) {
         is Failure -> wrapAndEncodeFailure(payload, buffer, payloadOffset())
         is Success -> wrapAndEncodeSuccess(payload, buffer, payloadOffset())
      }
   }

   fun encodeFrom(source: Msg) = apply {
      encodeMsg(source, msgEncoder, payloadHeaderEncoder, failureFW.failureEncoder, successFW.successEncoder)
   }

   fun decodeInto(target: Msg) = apply {
      decodeMsg(target, msgDecoder, payloadHeaderDecoder, failureFW.failureDecoder, successFW.successDecoder)
   }
}

fun Msg.printSbeLayout() {
   CSV()
      .info("Msg (totalSize = $SBE_SIZE_MSG bytes)")
      .addHeader("Offset", "Field", "Type", "SBE Type", "Size", "Value")
      .addRow("0", "[HEADER]", "Header", "", "${MessageHeaderEncoder.ENCODED_LENGTH}", "${MsgEncoder.BLOCK_LENGTH}|${MsgEncoder.TEMPLATE_ID}|${MsgEncoder.SCHEMA_ID}|${MsgEncoder.SCHEMA_VERSION}")
      .addRow("8", "payload", "Payload", "ByteArray24", "24", payload.toString())
      .addRow("32", "id", "TimeId", "int64", "8", id.toString())
      .print()
}

