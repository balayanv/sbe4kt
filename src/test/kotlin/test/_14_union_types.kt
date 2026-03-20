package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.META
import sbe4kt.SBE
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
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_WRAPPER1))
      val wrapperId = TimeId(Nanos(1000000000L))
      val successId = TimeId(Nanos(2000000000L))

      Wrapper1(
         id = wrapperId,
         payload = Success1(timestamp = successId, value = 42L)
      ).encodeTo(buffer)

      val decoded = newWrapper1()
      decoded.decodeFrom(buffer)

      assertEquals(wrapperId, decoded.id)
      assertTrue(decoded.payload is Success1)

      val success = decoded.payload as Success1
      assertEquals(successId, success.timestamp)
      assertEquals(42L, success.value)
   }

   @Test
   fun failureUnionEncodeDecodeRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_WRAPPER1))
      val wrapperId = TimeId(Nanos(3000000000L))
      val failureId = TimeId(Nanos(4000000000L))

      Wrapper1(
         id = wrapperId,
         payload = Failure1(timestamp = failureId, errorCode = 404)
      ).encodeTo(buffer)

      val decoded = newWrapper1()
      decoded.decodeFrom(buffer)

      assertEquals(wrapperId, decoded.id)
      assertTrue(decoded.payload is Failure1)

      val failure = decoded.payload as Failure1
      assertEquals(failureId, failure.timestamp)
      assertEquals(404, failure.errorCode)
   }

   @Test
   fun flyweightReadWriteSuccess() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_WRAPPER1))
      val w = Wrapper1W(buffer, 0)

      val wrapperId = TimeId(Nanos(5000000000L))
      val successId = TimeId(Nanos(6000000000L))

      w.id(wrapperId)
      w.payload(Success1(timestamp = successId, value = 999L))

      val r = Wrapper1R(buffer, 0)
      assertEquals(wrapperId, r.id())

      val retrievedPayload = r.payload()
      assertTrue(retrievedPayload is Success1)
      assertEquals(successId, (retrievedPayload as Success1).timestamp)
      assertEquals(999L, retrievedPayload.value)
   }

   @Test
   fun flyweightReadWriteFailure() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_WRAPPER1))
      val w = Wrapper1W(buffer, 0)

      val wrapperId = TimeId(Nanos(7000000000L))
      val failureId = TimeId(Nanos(8000000000L))

      w.id(wrapperId)
      w.payload(Failure1(timestamp = failureId, errorCode = 500))

      val r = Wrapper1R(buffer, 0)
      assertEquals(wrapperId, r.id())

      val retrievedPayload = r.payload()
      assertTrue(retrievedPayload is Failure1)
      assertEquals(failureId, (retrievedPayload as Failure1).timestamp)
      assertEquals(500, retrievedPayload.errorCode)
   }

   @Test
   fun flyweightEncodeFromAndDecodeInto() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_WRAPPER1))

      val original = Wrapper1(
         id = TimeId(Nanos(9000000000L)),
         payload = Success1(timestamp = TimeId(Nanos(10000000000L)), value = 777L)
      )

      val w = Wrapper1W(buffer, 0)
      w.encodeFrom(original)

      val target = newWrapper1()
      val r = Wrapper1R(buffer, 0)
      r.decodeInto(target)

      assertEquals(original.id, target.id)
      assertTrue(target.payload is Success1)
      assertEquals(777L, (target.payload as Success1).value)
   }

   @Test
   fun readOnlyFlyweight() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_WRAPPER1))

      Wrapper1(
         id = TimeId(Nanos(11000000000L)),
         payload = Failure1(timestamp = TimeId(Nanos(12000000000L)), errorCode = 403)
      ).encodeTo(buffer)

      val r = Wrapper1R(buffer, 0)
      assertEquals(TimeId(Nanos(11000000000L)), r.id())

      val payload = r.payload()
      assertTrue(payload is Failure1)
      assertEquals(403, (payload as Failure1).errorCode)
   }

   @Test
   fun writeOnlyFlyweight() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_WRAPPER1))
      val w = Wrapper1W(buffer, 0)

      w.id(TimeId(Nanos(13000000000L)))
      w.payload(Success1(timestamp = TimeId(Nanos(14000000000L)), value = 888L))

      val decoded = newWrapper1()
      decoded.decodeFrom(buffer)

      assertEquals(TimeId(Nanos(13000000000L)), decoded.id)
      assertTrue(decoded.payload is Success1)
      assertEquals(888L, (decoded.payload as Success1).value)
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
   fun switchingUnionTypes() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_WRAPPER1))
      val w = Wrapper1W(buffer, 0)
      val r = Wrapper1R(buffer, 0)

      w.id(TimeId(Nanos(15000000000L)))
      w.payload(Success1(timestamp = TimeId.now(), value = 100L))

      assertTrue(r.payload() is Success1)

      w.payload(Failure1(timestamp = TimeId.now(), errorCode = 404))

      r.wrap(buffer, 0)
      assertTrue(r.payload() is Failure1)
      assertEquals(404, (r.payload() as Failure1).errorCode)
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
   fun sealedInterfaceEncodeDecodeRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_WRAPPER2))

      Wrapper2(
         id = TimeId(Nanos(1000000000L)),
         payload = Service1Message.Success2(timestamp = TimeId(Nanos(2000000000L)), value = 42L)
      ).encodeTo(buffer)

      val decoded = newWrapper2()
      decoded.decodeFrom(buffer)

      assertEquals(TimeId(Nanos(1000000000L)), decoded.id)
      assertTrue(decoded.payload is Service1Message.Success2)
      assertEquals(42L, (decoded.payload as Service1Message.Success2).value)
   }

   @Test
   fun sealedInterfaceFailureRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_WRAPPER2))

      Wrapper2(
         id = TimeId(Nanos(3000000000L)),
         payload = Service1Message.Failure2(timestamp = TimeId(Nanos(4000000000L)), errorCode = 500)
      ).encodeTo(buffer)

      val decoded = newWrapper2()
      decoded.decodeFrom(buffer)

      assertEquals(TimeId(Nanos(3000000000L)), decoded.id)
      assertTrue(decoded.payload is Service1Message.Failure2)
      assertEquals(500, (decoded.payload as Service1Message.Failure2).errorCode)
   }

   @Test
   fun sealedInterfaceFlyweight() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_WRAPPER2))
      val w = Wrapper2W(buffer, 0)
      val r = Wrapper2R(buffer, 0)

      w.id(TimeId(Nanos(5000000000L)))
      w.payload(Service1Message.Success2(timestamp = TimeId(Nanos(6000000000L)), value = 999L))

      assertEquals(TimeId(Nanos(5000000000L)), r.id())
      assertTrue(r.payload() is Service1Message.Success2)
      assertEquals(999L, (r.payload() as Service1Message.Success2).value)

      w.payload(Service1Message.Failure2(timestamp = TimeId(Nanos(7000000000L)), errorCode = 404))

      r.wrap(buffer, 0)
      assertTrue(r.payload() is Service1Message.Failure2)
      assertEquals(404, (r.payload() as Service1Message.Failure2).errorCode)
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
   fun sealedInterfacePrintLayout() {
      Wrapper2(
         id = TimeId.now(),
         payload = Service1Message.Success2(TimeId.now(), 42L)
      ).printSbeLayout()
   }
}
