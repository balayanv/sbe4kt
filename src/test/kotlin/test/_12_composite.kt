package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE
import sbe4kt.lib.TimeId
import sbe4kt.lib.FPD
import sbe4kt.lib.Nanos

enum class Direction1 { BUY, SELL }

@SBE(isStruct = true)
data class XY1(
   var time: TimeId,
   var price: FPD,
)

@SBE
data class Signal1(
   var f1: Direction1,
   var f2: XY1,
)

class CompositeTest {

   @Test
   fun sizeIs25() {
      assertEquals(25, Signal1Codec.SIZE)
   }

   @Test
   fun flyweightCompositeAccess() {
      val buffer = UnsafeBuffer(ByteArray(1024))
      val fw = Signal1FW()
      fw.buffer.wrap(buffer, 100, Signal1Codec.SIZE)

      fw.writeF1(Direction1.SELL)
      fw.f2()
         .writeTime(TimeId(Nanos(9999000000L)))
         .writePrice(FPD.fromExact("12345.67"))

      assertEquals(Direction1.SELL, fw.readF1())
      assertEquals(TimeId(Nanos(9999000000L)), fw.f2().readTime())
      assertEquals(FPD.fromExact("12345.67"), fw.f2().readPrice())
   }

   @Test
   fun compositeEncodeDecodeRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(Signal1Codec.SIZE))

      val signal = Signal1(
         f1 = Direction1.BUY,
         f2 = XY1(time = TimeId(Nanos(1234000000L)), price = FPD.fromExact("78000.0")),
      )
      Signal1Codec.encodeFrom(signal, buffer, 0)

      val decoded = newSignal1()
      Signal1Codec.decodeInto(decoded, buffer, 0)

      assertEquals(Direction1.BUY, decoded.f1)
      assertEquals(TimeId(Nanos(1234000000L)), decoded.f2.time)
      assertEquals(FPD.fromExact("78000.0"), decoded.f2.price)
   }
}
