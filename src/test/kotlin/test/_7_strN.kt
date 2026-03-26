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
      assertEquals(60, Trade4Codec.SIZE)
   }

   @Test
   fun shortStringRoundtrips() {
      val buffer = UnsafeBuffer(ByteArray(Trade4Codec.SIZE))

      val trade = Trade4(id = 1L, symbol = "HI", timestamp = 2L, price = 3L, amount = 4L)
      Trade4Codec.encodeFrom(trade, buffer, 0)

      val decoded = newTrade4()
      Trade4Codec.decodeInto(decoded, buffer, 0)

      assertEquals("HI", decoded.symbol)
   }

   @Test
   fun fullLengthStringRoundtrips() {
      val buffer = UnsafeBuffer(ByteArray(Trade4Codec.SIZE))

      val trade = Trade4(id = 1L, symbol = "12345678901234567890", timestamp = 2L, price = 3L, amount = 4L)
      Trade4Codec.encodeFrom(trade, buffer, 0)

      val decoded = newTrade4()
      Trade4Codec.decodeInto(decoded, buffer, 0)

      assertEquals("12345678901234567890", decoded.symbol)
   }
}
