package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.*
import sbe4kt.lib.TimeId
import sbe4kt.lib.Nanos

@SBE
data class Wrapper1(
   var id: TimeId,
   @META(oneOf = [Failure1::class, Success1::class])
   var payload: Any,
)

@SBE
data class Failure1(var timestamp: TimeId, var errorCode: Int)

@SBE
data class Success1(var timestamp: TimeId, var value: Long)

@SBE
data class Wrapper2(
   var id: TimeId,
   var payload: Service1Message,
)

sealed interface Service1Message{

   @SBE
   data class Failure2(var timestamp: TimeId, var errorCode: Int) : Service1Message

   @SBE
   data class Success2(var timestamp: TimeId, var value: Long) : Service1Message
}

class UnionTypesTest {

   @Test
   fun successUnionEncodeDecodeRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(Wrapper1Codec.SIZE))
      val wrapperId = TimeId(Nanos(1000000000L))
      val successId = TimeId(Nanos(2000000000L))

      val wrapper = Wrapper1(
         id = wrapperId,
         payload = Success1(timestamp = successId, value = 42L)
      )
      Wrapper1Codec.encodeFrom(wrapper, buffer, 0)

      val decoded = newWrapper1()
      Wrapper1Codec.decodeInto(decoded, buffer, 0)

      assertEquals(wrapperId, decoded.id)
      assertTrue(decoded.payload is Success1)

      val success = decoded.payload as Success1
      assertEquals(successId, success.timestamp)
      assertEquals(42L, success.value)
   }

   @Test
   fun failureUnionEncodeDecodeRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(Wrapper1Codec.SIZE))
      val wrapperId = TimeId(Nanos(3000000000L))
      val failureId = TimeId(Nanos(4000000000L))

      val wrapper = Wrapper1(
         id = wrapperId,
         payload = Failure1(timestamp = failureId, errorCode = 404)
      )
      Wrapper1Codec.encodeFrom(wrapper, buffer, 0)

      val decoded = newWrapper1()
      Wrapper1Codec.decodeInto(decoded, buffer, 0)

      assertEquals(wrapperId, decoded.id)
      assertTrue(decoded.payload is Failure1)

      val failure = decoded.payload as Failure1
      assertEquals(failureId, failure.timestamp)
      assertEquals(404, failure.errorCode)
   }

   @Test
   fun flyweightReadWriteSuccess() {
      val buffer = UnsafeBuffer(ByteArray(Wrapper1Codec.SIZE))
      val fw = Wrapper1FW(buffer)

      val wrapperId = TimeId(Nanos(5000000000L))
      val successId = TimeId(Nanos(6000000000L))

      fw.id(wrapperId)
      fw.payload(Success1(timestamp = successId, value = 999L))

      assertEquals(wrapperId, fw.id())
      assertEquals(Success1Codec.TEMPLATE_ID, fw.payloadId())

      val success = fw.payloadSuccess1()
      assertEquals(successId, success.timestamp())
      assertEquals(999L, success.value())
   }

   @Test
   fun flyweightReadWriteFailure() {
      val buffer = UnsafeBuffer(ByteArray(Wrapper1Codec.SIZE))
      val fw = Wrapper1FW(buffer)

      val wrapperId = TimeId(Nanos(7000000000L))
      val failureId = TimeId(Nanos(8000000000L))

      fw.id(wrapperId)
      fw.payload(Failure1(timestamp = failureId, errorCode = 500))

      assertEquals(wrapperId, fw.id())
      assertEquals(Failure1Codec.TEMPLATE_ID, fw.payloadId())

      val failure = fw.payloadFailure1()
      assertEquals(failureId, failure.timestamp())
      assertEquals(500, failure.errorCode())
   }

   @Test
   fun flyweightEncodeFromAndDecodeInto() {
      val buffer = UnsafeBuffer(ByteArray(Wrapper1Codec.SIZE))
      val fw = Wrapper1FW(buffer)

      val original = Wrapper1(
         id = TimeId(Nanos(9000000000L)),
         payload = Success1(timestamp = TimeId(Nanos(10000000000L)), value = 777L)
      )

      fw.encodeFrom(original)

      val target = newWrapper1()
      fw.decodeInto(target)

      assertEquals(original.id, target.id)
      assertTrue(target.payload is Success1)
      assertEquals(777L, (target.payload as Success1).value)
   }

   @Test
   fun readOnlyFlyweight() {
      val buffer = UnsafeBuffer(ByteArray(Wrapper1Codec.SIZE))

      val wrapper = Wrapper1(
         id = TimeId(Nanos(11000000000L)),
         payload = Failure1(timestamp = TimeId(Nanos(12000000000L)), errorCode = 403)
      )
      Wrapper1Codec.encodeFrom(wrapper, buffer, 0)

      val fwR = Wrapper1FW(buffer)
      assertEquals(TimeId(Nanos(11000000000L)), fwR.id())

      assertEquals(Failure1Codec.TEMPLATE_ID, fwR.payloadId())
      val failure = fwR.payloadFailure1()
      assertEquals(403, failure.errorCode())
   }

   @Test
   fun writeOnlyFlyweight() {
      val buffer = UnsafeBuffer(ByteArray(Wrapper1Codec.SIZE))
      val fwW = Wrapper1FW(buffer)

      fwW.header()
      fwW.id(TimeId(Nanos(13000000000L)))
      fwW.payload(Success1(timestamp = TimeId(Nanos(14000000000L)), value = 888L))

      val decoded = newWrapper1()
      Wrapper1Codec.decodeInto(decoded, buffer, 0)

      assertEquals(TimeId(Nanos(13000000000L)), decoded.id)
      assertTrue(decoded.payload is Success1)
      assertEquals(888L, (decoded.payload as Success1).value)
   }

   @Test
   fun switchingUnionTypes() {
      val buffer = UnsafeBuffer(ByteArray(Wrapper1Codec.SIZE))
      val fw = Wrapper1FW(buffer)

      fw.id(TimeId(Nanos(15000000000L)))
      fw.payload(Success1(timestamp = TimeId.now(), value = 100L))

      assertEquals(Success1Codec.TEMPLATE_ID, fw.payloadId())

      fw.payload(Failure1(timestamp = TimeId.now(), errorCode = 404))

      assertEquals(Failure1Codec.TEMPLATE_ID, fw.payloadId())
      assertEquals(404, fw.payloadFailure1().errorCode())
   }

   @Test
   fun sealedInterfaceEncodeDecodeRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(Wrapper2Codec.SIZE))

      val wrapper = Wrapper2(
         id = TimeId(Nanos(1000000000L)),
         payload = Service1Message.Success2(timestamp = TimeId(Nanos(2000000000L)), value = 42L)
      )
      Wrapper2Codec.encodeFrom(wrapper, buffer, 0)

      val decoded = newWrapper2()
      Wrapper2Codec.decodeInto(decoded, buffer, 0)

      assertEquals(TimeId(Nanos(1000000000L)), decoded.id)
      assertTrue(decoded.payload is Service1Message.Success2)
      assertEquals(42L, (decoded.payload as Service1Message.Success2).value)
   }

   @Test
   fun sealedInterfaceFailureRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(Wrapper2Codec.SIZE))

      val wrapper = Wrapper2(
         id = TimeId(Nanos(3000000000L)),
         payload = Service1Message.Failure2(timestamp = TimeId(Nanos(4000000000L)), errorCode = 500)
      )
      Wrapper2Codec.encodeFrom(wrapper, buffer, 0)

      val decoded = newWrapper2()
      Wrapper2Codec.decodeInto(decoded, buffer, 0)

      assertEquals(TimeId(Nanos(3000000000L)), decoded.id)
      assertTrue(decoded.payload is Service1Message.Failure2)
      assertEquals(500, (decoded.payload as Service1Message.Failure2).errorCode)
   }

   @Test
   fun sealedInterfaceFlyweight() {
      val fw = Wrapper2FW()

      fw.id(TimeId(Nanos(5000000000L)))
      fw.payload(Service1Message.Success2(timestamp = TimeId(Nanos(6000000000L)), value = 999L))

      assertEquals(TimeId(Nanos(5000000000L)), fw.id())
      assertEquals(Success2Codec.TEMPLATE_ID, fw.payloadId())
      assertEquals(999L, fw.payloadSuccess2().value())

      fw.payload(Service1Message.Failure2(timestamp = TimeId(Nanos(7000000000L)), errorCode = 404))

      assertEquals(Failure2Codec.TEMPLATE_ID, fw.payloadId())
      assertEquals(404, fw.payloadFailure2().errorCode())
   }

   @Test
   fun smartCastInWhen() {
      val msg = Wrapper1(
         id = TimeId.now(),
         payload = Success1(TimeId.now(), 42L)
      )

      when (val payload = msg.payload) {
         is Success1 -> assertEquals(42L, payload.value)
         is Failure1 -> fail("Expected Success but got Failure")
      }
   }

   @Test
   fun sealedInterfaceExhaustiveWhen() {
      val msg = Wrapper2(
         id = TimeId.now(),
         payload = Service1Message.Success2(TimeId.now(), 42L)
      )

      val result = when (msg.payload) {
         is Service1Message.Success2 -> "success"
         is Service1Message.Failure2 -> "failure"
      }
      assertEquals("success", result)
   }

   @Test
   fun printLayouts() {
      Wrapper1(
         id = TimeId.now(),
         payload = Success1(TimeId.now(), 42L)
      ).printSbeLayout()

      randomSuccess1().printSbeLayout()
      randomFailure1().printSbeLayout()
   }

   @Test
   fun sealedInterfacePrintLayout() {
      Wrapper2(
         id = TimeId.now(),
         payload = Service1Message.Success2(TimeId.now(), 42L)
      ).printSbeLayout()
   }
}
