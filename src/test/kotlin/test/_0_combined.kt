package test.all

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sbe4kt.SBE

@SBE
data class AllMessage(
   var f1: Boolean,
   var f2: Char,
   var f3: Byte,
   var f4: Short,
   var f5: Int,
   var f6: Long,
   var f7: Float,
   var f8: Double,
   var f9: EnumBool,
   var f10: ValueBool
)

enum class EnumBool {
   FALSE, TRUE
}

@JvmInline
value class ValueBool(val value: Boolean)


class AllTest {

   @Test
   fun testRandomValues() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_ALL_MESSAGE))
      repeat(100) {
         val original = randomAllMessage()
         val bytesWritten = original.encodeTo(buffer)
         assertTrue(bytesWritten > 0)
         val decoded = randomAllMessage()
         val bytesRead = decoded.decodeFrom(buffer)
         assertEquals(bytesWritten, bytesRead)
         assertEquals(original, decoded)
      }
   }

   @Test
   fun testFlyweights() {
      val wrapper = AllMessageRW()
      repeat(100) {
         val original = randomAllMessage()
         wrapper.encode(original)

         val decoded = newAllMessage()
         wrapper.decode(decoded)

         assertEquals(original, decoded)
      }
   }
}