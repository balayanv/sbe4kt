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
      assertEquals(33, Signal2Codec.SIZE)
   }

   @Test
   fun nestedMessageRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(Signal2Codec.SIZE))

      val signal = Signal2(
         direction = Direction2.SELL,
         xy = XY2(time = TimeId(Nanos(1000000000L)), price = FPD.fromExact("100.5")),
      )
      Signal2Codec.encodeFrom(signal, buffer, 0)

      val decoded = newSignal2()
      Signal2Codec.decodeInto(decoded, buffer, 0)

      assertEquals(Direction2.SELL, decoded.direction)
      assertEquals(TimeId(Nanos(1000000000L)), decoded.xy.time)
      assertEquals(FPD.fromExact("100.5"), decoded.xy.price)
   }

   @Test
   fun flyweightNestedMessageAccess() {
      val buffer = UnsafeBuffer(ByteArray(Signal2Codec.SIZE))
      val fw = Signal2FW(buffer)

      fw.writeDirection(Direction2.BUY)
      fw.xy()
         .writeTime(TimeId(Nanos(7777000000L)))
         .writePrice(FPD.fromExact("55.55"))

      assertEquals(Direction2.BUY, fw.readDirection())
      assertEquals(TimeId(Nanos(7777000000L)), fw.xy().readTime())
      assertEquals(FPD.fromExact("55.55"), fw.xy().readPrice())
   }
}
