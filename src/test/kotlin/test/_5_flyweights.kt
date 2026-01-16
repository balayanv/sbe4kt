package test.flyweights

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sbe4kt.Message

@Message
data class SimpleMessage(
   var intField: Int,
   var longField: Long,
   var booleanField: Boolean,
   var doubleField: Double
)

class FlyweightTest {

   @Test
   fun testFlyweightRWReadWrite() {
      val msgRW = SimpleMessageRW()

      // Test writing object into buffer
      val original = newSimpleMessage(intField = 42, longField = 100L, booleanField = true, doubleField = 3.14)
      msgRW.encode(original)

      // Test reading direct from buffer
      assertEquals(42, msgRW.intField())
      assertEquals(100L, msgRW.longField())
      assertEquals(true, msgRW.booleanField())
      assertEquals(3.14, msgRW.doubleField())

      // Test writing direct to buffer
      msgRW.intField(24)
      msgRW.longField(200L)
      msgRW.booleanField(false)
      msgRW.doubleField(2.71)

      // Test reading buffer into object
      val decoded = newSimpleMessage()
      msgRW.decode(decoded)

      assertEquals(24, decoded.intField)
      assertEquals(200L, decoded.longField)
      assertEquals(false, decoded.booleanField)
      assertEquals(2.71, decoded.doubleField)
   }

   @Test
   fun testFlyweightWriteOnly() {
      val msgW = SimpleMessageW()

      // Test setting fields
      msgW.intField(99)
         .longField(999L)
         .booleanField(true)
         .doubleField(9.99)

      // Verify by decoding
      val decoded = newSimpleMessage()
      decodeSimpleMessage(decoded, msgW.buffer)

      assertEquals(99, decoded.intField)
      assertEquals(999L, decoded.longField)
      assertEquals(true, decoded.booleanField)
      assertEquals(9.99, decoded.doubleField)
   }

   @Test
   fun testFlyweightReadOnly() {
      val msgR = SimpleMessageR()

      // First encode some data
      val original = newSimpleMessage(intField = 55, longField = 555L, booleanField = false, doubleField = 5.55)
      encodeSimpleMessage(original, msgR.buffer)

      assertEquals(55, msgR.intField())
      assertEquals(555L, msgR.longField())
      assertEquals(false, msgR.booleanField())
      assertEquals(5.55, msgR.doubleField())

      // Test decode to object
      val decoded = msgR.decode()
      assertEquals(original, decoded)
   }

   @Test
   fun testFlyweightWithOffset() {
      val bufferSize = SBE_SIZE_SIMPLE_MESSAGE * 3
      val buffer = UnsafeBuffer(ByteArray(bufferSize))

      // Write three messages at different offsets
      val msg1 = newSimpleMessage(intField = 1, longField = 10L, booleanField = true, doubleField = 1.1)
      val msg2 = newSimpleMessage(intField = 2, longField = 20L, booleanField = false, doubleField = 2.2)
      val msg3 = newSimpleMessage(intField = 3, longField = 30L, booleanField = true, doubleField = 3.3)

      encodeSimpleMessage(msg1, buffer, 0)
      encodeSimpleMessage(msg2, buffer, SBE_SIZE_SIMPLE_MESSAGE)
      encodeSimpleMessage(msg3, buffer, SBE_SIZE_SIMPLE_MESSAGE * 2)

      // Read using flyweight at different offsets
      val msgRW1 = SimpleMessageRW().wrap(buffer, 0)
      val msgRW2 = SimpleMessageRW().wrap(buffer, SBE_SIZE_SIMPLE_MESSAGE)
      val msgRW3 = SimpleMessageRW().wrap(buffer, SBE_SIZE_SIMPLE_MESSAGE * 2)

      assertEquals(1, msgRW1.intField())
      assertEquals(2, msgRW2.intField())
      assertEquals(3, msgRW3.intField())

      // Modify via flyweight
      msgRW2.intField(22)
      msgRW2.longField(220L)

      // Verify modification
      val decoded2 = newSimpleMessage()
      decodeSimpleMessage(decoded2, buffer, SBE_SIZE_SIMPLE_MESSAGE)
      assertEquals(22, decoded2.intField)
      assertEquals(220L, decoded2.longField)
   }

   @Test
   fun testFlyweightChaining() {
      val msgRW = SimpleMessageRW()

      // Test method chaining for write operations
      msgRW.intField(10)
         .longField(100L)
         .booleanField(true)
         .doubleField(1.23)

      // Verify all values were set
      assertEquals(10, msgRW.intField())
      assertEquals(100L, msgRW.longField())
      assertEquals(true, msgRW.booleanField())
      assertEquals(1.23, msgRW.doubleField())
   }

   @Test
   fun testFlyweightPerformance() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_SIMPLE_MESSAGE))
      val msgRW = SimpleMessageRW().wrap(buffer, 0)

      // Simulate many read/write operations without object creation
      repeat(1000) { i ->
         // Direct write to buffer
         msgRW.intField(i)
         msgRW.longField(i.toLong() * 1000)
         msgRW.booleanField(i % 2 == 0)

         // Direct read from buffer
         val intVal = msgRW.intField()
         val longVal = msgRW.longField()
         val boolVal = msgRW.booleanField()

         // Verify
         assertEquals(i, intVal)
         assertEquals(i.toLong() * 1000, longVal)
         assertEquals(i % 2 == 0, boolVal)
      }
   }
}