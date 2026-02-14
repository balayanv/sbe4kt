package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE
import sbe4kt.lib.CharSet

object TestSymbol1 : CharSet("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_")

@SBE
data class Trade3(
   var id: Long,
   var symbol: TestSymbol1Str64,
   var timestamp: Long,
   var price: Long,
   var amount: Long,
)

class Str64Test {

   @Test
   fun maxCapIs10() {
      assertEquals(10, TestSymbol1Str64.codec.maxCap)
      randomTrade3().printSbeLayout()
   }

   @Test
   fun shortSymbolRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_TRADE3))

      val trade = Trade3(id = 1L, symbol = TestSymbol1Str64.from("ABC"), timestamp = 2L, price = 3L, amount = 4L)
      wrapAndEncodeTrade3(trade, buffer)

      val decoded = newTrade3()
      wrapAndDecodeTrade3(decoded, buffer)

      assertEquals("ABC", decoded.symbol.toString())
   }

   @Test
   fun fullCapSymbolRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_TRADE3))

      val trade = Trade3(id = 1L, symbol = TestSymbol1Str64.from("XBTUSDTH26"), timestamp = 2L, price = 3L, amount = 4L)
      wrapAndEncodeTrade3(trade, buffer)

      val decoded = newTrade3()
      wrapAndDecodeTrade3(decoded, buffer)

      assertEquals("XBTUSDTH26", decoded.symbol.toString())
   }

   @Test
   fun flyweightStr64() {
      val fw = Trade3RW()
      fw.symbol(TestSymbol1Str64.from("ETH_USD"))
      assertEquals("ETH_USD", fw.symbol().toString())
   }
}
