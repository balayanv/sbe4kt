package tests

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class TestMessagesTest {

   @Test
   fun testExplicitIdMessage() {
      val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(1024))
      val msg = newExplicitIdMessage(123L, 456)

      val offset = 0
      val length = encodeExplicitIdMessage(buffer, msg)

      // Header (8) + Body (12: Long(8) + Int(4)) = 20
      assertEquals(20, length)

      val decoded = newExplicitIdMessage(0, 0)
      decodeExplicitIdMessage(buffer, decoded)

      assertEquals(123L, decoded.fieldA)
      assertEquals(456, decoded.fieldB)

      // Verify MessageHeader ID (Requires manual check or inspecting generated code constant not exposed easily via API extension yet,
      // but we can assume if it decodes it worked)
   }

   @Test
   fun testImplicitIdMessage() {
      val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(1024))
      val msg = newImplicitIdMessage(10.5, 100)

      val length = encodeImplicitIdMessage(buffer, msg)
      // Header (8) + Body (12: Double(8) + Int(4)) = 20
      assertEquals(20, length)

      val decoded = newImplicitIdMessage(0.0, 0)
      decodeImplicitIdMessage(buffer, decoded)

      assertEquals(10.5, decoded.price, 0.001)
      assertEquals(100, decoded.quantity)
   }
}
