package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE
import sbe4kt.lib.TimeId
import sbe4kt.lib.FPD
import sbe4kt.lib.Nanos

enum class Direction2 { BUY, SELL }

@SBE
data class XY2(
   var time: TimeId,
   var price: FPD,
)

@SBE
data class Signal2(
   var direction: Direction2,
   var xy: XY2,
)

class NestedMessageTest {

   @Test
   fun signal2SizeIs33() {
      assertEquals(33, SBE_SIZE_SIGNAL2)
   }

   @Test
   fun nestedMessageRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_SIGNAL2))

      Signal2(
         direction = Direction2.SELL,
         xy = XY2(time = TimeId(Nanos(1000000000L)), price = FPD.fromExact("100.5")),
      ).encodeTo(buffer)

      val decoded = newSignal2()
      decoded.decodeFrom(buffer)

      assertEquals(Direction2.SELL, decoded.direction)
      assertEquals(TimeId(Nanos(1000000000L)), decoded.xy.time)
      assertEquals(FPD.fromExact("100.5"), decoded.xy.price)
   }

   @Test
   fun flyweightNestedMessageAccess() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_SIGNAL2))

      val source = Signal2(
         direction = Direction2.BUY,
         xy = XY2(time = TimeId(Nanos(7777000000L)), price = FPD.fromExact("55.55"))
      )
      val w = Signal2W(buffer, 0)
      w.encodeFrom(source)

      val target = newSignal2()
      val r = Signal2R(buffer, 0)
      r.decodeInto(target)

      assertEquals(Direction2.BUY, target.direction)
      assertEquals(TimeId(Nanos(7777000000L)), target.xy.time)
      assertEquals(FPD.fromExact("55.55"), target.xy.price)
   }
}
