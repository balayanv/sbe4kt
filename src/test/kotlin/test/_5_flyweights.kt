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
      val fw = Trade2FW()
      fw.header().id(1111L).symbol(42).timestamp(2222L).price(3333L).amount(4444L)

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
      val buffer = UnsafeBuffer(ByteArray(Trade2Codec.SIZE))

      Trade2FW(buffer).id(100L).symbol(200).timestamp(300L).price(400L).amount(500L)

      val r = Trade2FW(buffer)
      assertEquals(100L, r.id())
      assertEquals(200, r.symbol())
      assertEquals(300L, r.timestamp())
      assertEquals(400L, r.price())
      assertEquals(500L, r.amount())
   }

   @Test
   fun bufferWrapRepositions() {
      val data = ByteArray(Trade2Codec.SIZE * 2)
      val fw = Trade2FW()
      fw.buffer.wrap(data, 0, Trade2Codec.SIZE)
      fw.id(1L)

      fw.buffer.wrap(data, Trade2Codec.SIZE, Trade2Codec.SIZE)
      fw.id(2L)

      val r = Trade2FW()
      r.buffer.wrap(data, 0, Trade2Codec.SIZE)
      assertEquals(1L, r.id())

      r.buffer.wrap(data, Trade2Codec.SIZE, Trade2Codec.SIZE)
      assertEquals(2L, r.id())
   }
}
