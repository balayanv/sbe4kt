package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE
import sbe4kt.lib.FPD5
import sbe4kt.lib.FPD

@SBE
data class Msg6(var f1: FPD5)

@SBE
data class Msg7(var f1: FPD)

class FPDTest {

   @Test
   fun fpd5ArithmeticSurvivesRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(Msg6Codec.SIZE))
      val sum = FPD5.fromExact("1111.11111") + FPD5.fromExact("22.22222")

      val msg = Msg6(f1 = sum)
      Msg6Codec.encodeFrom(msg, buffer, 0)

      val decoded = newMsg6()
      Msg6Codec.decodeInto(decoded, buffer, 0)

      assertEquals("1133.33333", decoded.f1.toString())
   }

   @Test
   fun fpdCrossScaleArithmeticSurvivesRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(Msg7Codec.SIZE))
      val sum = FPD.fromExact("1111.11111") + FPD.fromExact("22.222")

      val msg = Msg7(f1 = sum)
      Msg7Codec.encodeFrom(msg, buffer, 0)

      val decoded = newMsg7()
      Msg7Codec.decodeInto(decoded, buffer, 0)

      assertEquals("1133.33311", decoded.f1.toString())
   }
}
