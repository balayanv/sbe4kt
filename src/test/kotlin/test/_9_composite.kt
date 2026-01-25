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
      assertEquals(25, SBE_SIZE_SIGNAL1)
   }

   @Test
   fun flyweightCompositeAccess() {
      val buffer = UnsafeBuffer(ByteArray(1024))
      val fw = Signal1RW(buffer, 100)

      fw.f1(Direction1.SELL)
      fw.f2FW()
         .time(TimeId(Nanos(9999000000L)))
         .price(FPD.fromExact("12345.67"))

      assertEquals(Direction1.SELL, fw.f1())
      assertEquals(TimeId(Nanos(9999000000L)), fw.f2FW().time())
      assertEquals(FPD.fromExact("12345.67"), fw.f2FW().price())
   }

   @Test
   fun compositeEncodeDecodeRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_SIGNAL1))

      Signal1(
         f1 = Direction1.BUY,
         f2 = XY1(time = TimeId(Nanos(1234000000L)), price = FPD.fromExact("78000.0")),
      ).encodeTo(buffer)

      val decoded = newSignal1()
      decoded.decodeFrom(buffer)

      assertEquals(Direction1.BUY, decoded.f1)
      assertEquals(TimeId(Nanos(1234000000L)), decoded.f2.time)
      assertEquals(FPD.fromExact("78000.0"), decoded.f2.price)
   }
}
