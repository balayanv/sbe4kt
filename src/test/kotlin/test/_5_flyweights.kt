package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE

@SBE
data class Trade2(
   var id: Long,
   var symbol: Int,
   var timestamp: Long,
   var price: Long,
   var amount: Long,
)

class FlyweightsTest {

   @Test
   fun rwDirectSetAndGet() {
      val fw = Trade2RW()
      fw.id(1111L).symbol(42).timestamp(2222L).price(3333L).amount(4444L)

      assertEquals(1111L, fw.id())
      assertEquals(42, fw.symbol())
      assertEquals(2222L, fw.timestamp())
      assertEquals(3333L, fw.price())
      assertEquals(4444L, fw.amount())

      val temp = newTrade2()
      fw.decodeInto(target = temp)

      temp.printSbeLayout()
   }

   @Test
   fun writeOnlyThenReadOnly() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_TRADE2))

      Trade2RW(buffer, 0)
         .id(100L).symbol(200).timestamp(300L).price(400L).amount(500L)

      val r = Trade2RW(buffer, 0)
      assertEquals(100L, r.id())
      assertEquals(200, r.symbol())
      assertEquals(300L, r.timestamp())
      assertEquals(400L, r.price())
      assertEquals(500L, r.amount())
   }

   @Test
   fun wrapRepositions() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_TRADE2 * 2))
      val fw = Trade2RW(buffer, 0)

      fw.id(1L)
      fw.wrap(buffer, SBE_SIZE_TRADE2)
      fw.id(2L)

      assertEquals(1L, Trade2RW(buffer, 0).id())
      assertEquals(2L, Trade2RW(buffer, SBE_SIZE_TRADE2).id())
   }
}
