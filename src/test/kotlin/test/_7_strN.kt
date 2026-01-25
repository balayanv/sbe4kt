package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE
import sbe4kt.META

@SBE
data class Trade4(
   var id: Long,
   @META(size = 20)
   var symbol: String,
   var timestamp: Long,
   var price: Long,
   var amount: Long,
)

class StrNTest {

   @Test
   fun sizeIs60() {
      assertEquals(60, SBE_SIZE_TRADE4)
   }

   @Test
   fun shortStringRoundtrips() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_TRADE4))

      Trade4(id = 1L, symbol = "HI", timestamp = 2L, price = 3L, amount = 4L).encodeTo(buffer)

      val decoded = newTrade4()
      decoded.decodeFrom(buffer)

      assertEquals("HI", decoded.symbol)
   }

   @Test
   fun fullLengthStringRoundtrips() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_TRADE4))

      Trade4(id = 1L, symbol = "12345678901234567890", timestamp = 2L, price = 3L, amount = 4L).encodeTo(buffer)

      val decoded = newTrade4()
      decoded.decodeFrom(buffer)

      assertEquals("12345678901234567890", decoded.symbol)
   }
}
