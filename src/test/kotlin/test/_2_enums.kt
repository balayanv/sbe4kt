package test.enums

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sbe4kt.Message
import test.enums.MessageWithEnums.Status

enum class Color(val value: Int) {
   RED(0), GREEN(1), BLUE(2), YELLOW(3), PURPLE(4)
}

@Message
data class MessageWithEnums(
   var color: Color,
   var status: Status,
   var priority: Int
) {

   enum class Status(val value: Int) {
      PENDING(0), ACTIVE(1), COMPLETED(2), CANCELLED(3)
   }
}

class EnumsTest {
   @Test
   fun testEnumSerialization() {
      val original = MessageWithEnums(
         color = Color.BLUE,
         status = Status.ACTIVE,
         priority = 5
      )
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_MESSAGE_WITH_ENUMS))

      val bytesWritten = original.encodeTo(buffer)
      val decoded = newMessageWithEnums()
      val bytesRead = decoded.decodeFrom(buffer)

      assertEquals(bytesWritten, bytesRead)
      assertEquals(original, decoded)
   }

   @Test
   fun testDifferentEnumCombinations() {
      val original = MessageWithEnums(
         color = Color.GREEN,
         status = Status.COMPLETED,
         priority = 1
      )
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_MESSAGE_WITH_ENUMS))

      original.encodeTo(buffer)
      val decoded = newMessageWithEnums()
      decoded.decodeFrom(buffer)

      assertEquals(original, decoded)
   }

   @Test
   fun testAllEnumValues() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_MESSAGE_WITH_ENUMS))
      for (color in Color.entries) {
         for (status in Status.entries) {
            val original = MessageWithEnums(color, status, 42)
            original.encodeTo(buffer)
            val decoded = newMessageWithEnums()
            decoded.decodeFrom(buffer)
            assertEquals(original, decoded)
         }
      }
   }

   @Test
   fun testRandomEnumValues() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_MESSAGE_WITH_ENUMS))
      repeat(50) {
         val original = randomMessageWithEnums()
         original.encodeTo(buffer)
         val decoded = newMessageWithEnums()
         decoded.decodeFrom(buffer)
         assertEquals(original, decoded)
      }
   }
}