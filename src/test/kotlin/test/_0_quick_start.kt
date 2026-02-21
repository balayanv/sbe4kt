package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE

@SBE
data class Trade1(
   var id: Long,
   var symbol: Int,
   var timestamp: Long,
   var price: Long,
   var amount: Long,
)

class QuickStartTest {

   @Test
   fun sizeIs44() {
      assertEquals(44, Trade1Codec.SIZE)
   }

   @Test
   fun encodeAndDecode() {
      val trade = Trade1(id = 111L, symbol = 42, timestamp = 222L, price = 333L, amount = 444L)
      val buffer = UnsafeBuffer(ByteArray(Trade1Codec.SIZE))

      encodeTrade1(trade, buffer)

      val decoded = newTrade1()
      decodeTrade1(decoded, buffer)

      assertEquals(trade, decoded)
   }
}
