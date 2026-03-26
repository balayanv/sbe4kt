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
      fw.writeHeader().writeId(1111L).writeSymbol(42).writeTimestamp(2222L).writePrice(3333L).writeAmount(4444L)

      assertEquals(1111L, fw.readId())
      assertEquals(42, fw.readSymbol())
      assertEquals(2222L, fw.readTimestamp())
      assertEquals(3333L, fw.readPrice())
      assertEquals(4444L, fw.readAmount())

      val temp = newTrade2()
      fw.decode(target = temp)

      temp.printSbeLayout()
   }

   @Test
   fun writeOnlyThenReadOnly() {
      val buffer = UnsafeBuffer(ByteArray(Trade2Codec.SIZE))

      Trade2FW(buffer).writeId(100L).writeSymbol(200).writeTimestamp(300L).writePrice(400L).writeAmount(500L)

      val r = Trade2FW(buffer)
      assertEquals(100L, r.readId())
      assertEquals(200, r.readSymbol())
      assertEquals(300L, r.readTimestamp())
      assertEquals(400L, r.readPrice())
      assertEquals(500L, r.readAmount())
   }

   @Test
   fun bufferWrapRepositions() {
      val data = ByteArray(Trade2Codec.SIZE * 2)
      val fw = Trade2FW()
      fw.buffer.wrap(data, 0, Trade2Codec.SIZE)
      fw.writeId(1L)

      fw.buffer.wrap(data, Trade2Codec.SIZE, Trade2Codec.SIZE)
      fw.writeId(2L)

      val r = Trade2FW()
      r.buffer.wrap(data, 0, Trade2Codec.SIZE)
      assertEquals(1L, r.readId())

      r.buffer.wrap(data, Trade2Codec.SIZE, Trade2Codec.SIZE)
      assertEquals(2L, r.readId())
   }
}
