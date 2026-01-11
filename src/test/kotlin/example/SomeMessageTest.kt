package example

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class SomeMessageTest {

   @Test
   fun testAllPrimitiveFieldsSerialization() {
      val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(1024))
      val original = randomSomeMessage()
      val bytesWritten = encodeSomeMessage(buffer, original)

      assertEquals(37, bytesWritten, "Encoded message size should be 37 bytes")

      val decoded = newSomeMessage()
      val bytesRead = decodeSomeMessage(buffer, decoded)

      assertEquals(bytesWritten, bytesRead, "Bytes read should match bytes written")
      assertEquals(original.f1, decoded.f1, "Boolean field should match")
      assertEquals(original.f2, decoded.f2, "Char field should match")
      assertEquals(original.f3, decoded.f3, "Byte field should match")
      assertEquals(original.f4, decoded.f4, "Short field should match")
      assertEquals(original.f5, decoded.f5, "Int field should match")
      assertEquals(original.f6, decoded.f6, "Long field should match")
      assertEquals(original.f7, decoded.f7, 0.0001f, "Float field should match")
      assertEquals(original.f8, decoded.f8, 0.000001, "Double field should match")
   }

   @Test
   fun testDefaultValues() {
      val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(1024))
      val original = newSomeMessage()
      val bytesWritten = encodeSomeMessage(buffer, original)

      val decoded = newSomeMessage()
      val bytesRead = decodeSomeMessage(buffer, decoded)

      assertEquals(bytesWritten, bytesRead)
      assertEquals(false, decoded.f1)
      assertEquals('\u0000', decoded.f2)
      assertEquals(0.toByte(), decoded.f3)
      assertEquals(0.toShort(), decoded.f4)
      assertEquals(0, decoded.f5)
      assertEquals(0L, decoded.f6)
      assertEquals(0.0f, decoded.f7)
      assertEquals(0.0, decoded.f8)
   }

   @Test
   fun testRandomMessage() {
      val buffer = UnsafeBuffer(ByteBuffer.allocateDirect(1024))
      val original = randomSomeMessage()
      val bytesWritten = encodeSomeMessage(buffer, original)

      val decoded = newSomeMessage()
      val bytesRead = decodeSomeMessage(buffer, decoded)

      assertEquals(bytesWritten, bytesRead)
      assertEquals(original.f1, decoded.f1)
      assertEquals(original.f2, decoded.f2)
      assertEquals(original.f3, decoded.f3)
      assertEquals(original.f4, decoded.f4)
      assertEquals(original.f5, decoded.f5)
      assertEquals(original.f6, decoded.f6)
      assertEquals(original.f7, decoded.f7, 0.0001f)
      assertEquals(original.f8, decoded.f8, 0.000001)
   }
}
