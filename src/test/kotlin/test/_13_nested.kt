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

      val signal = Signal2(
         direction = Direction2.SELL,
         xy = XY2(time = TimeId(Nanos(1000000000L)), price = FPD.fromExact("100.5")),
      )
      wrapAndEncodeSignal2(signal, buffer)

      val decoded = newSignal2()
      wrapAndDecodeSignal2(decoded, buffer)

      assertEquals(Direction2.SELL, decoded.direction)
      assertEquals(TimeId(Nanos(1000000000L)), decoded.xy.time)
      assertEquals(FPD.fromExact("100.5"), decoded.xy.price)
   }

   @Test
   fun flyweightNestedMessageAccess() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_SIGNAL2))
      val fw = Signal2RW(buffer, 0)

      fw.direction(Direction2.BUY)
      fw.xy()
         .time(TimeId(Nanos(7777000000L)))
         .price(FPD.fromExact("55.55"))

      assertEquals(Direction2.BUY, fw.direction())
      assertEquals(TimeId(Nanos(7777000000L)), fw.xy().time())
      assertEquals(FPD.fromExact("55.55"), fw.xy().price())
   }
}
