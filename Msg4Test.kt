package test

import kotlin.random.Random
import org.agrona.DirectBuffer
import org.agrona.MutableDirectBuffer
import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sbe4kt.lib.CSV

internal class Msg4Test {

   @Test
   fun testCodecRoundTrip() {
      val buffer = UnsafeBuffer(ByteArray(Msg4Codec.SIZE))
      repeat(100) {
         val original = randomMsg4()
         encodeMsg4(original, buffer)

         val decoded = newMsg4()
         decodeMsg4(decoded, buffer)

         assertEquals(original, decoded)
      }
   }

   @Test
   fun testFlyweightRoundTrip() {
      val rw = Msg4RW()
      repeat(100) {
         val original = randomMsg4()
         rw.encodeFrom(original)

         val decoded = newMsg4()
         rw.decodeInto(decoded)

         assertEquals(original, decoded)
      }
   }

   @Test
   fun testCodecCorrectness() {
      val buffer1 = UnsafeBuffer(ByteArray(Msg4Codec.SIZE))
      val buffer2 = UnsafeBuffer(ByteArray(Msg4Codec.SIZE))

      repeat(100) {
         val original = randomMsg4()
         val decodedFrom2 = newMsg4()
         val decodedFrom1 = newMsg4()

         encodeMsg4(original, buffer1)
         decodeMsg4SBE(decodedFrom1, buffer1)

         encodeMsg4SBE(original, buffer2)
         decodeMsg4(decodedFrom2, buffer2)

         assertEquals(buffer1, buffer2)
         assertEquals(original, decodedFrom1)
         assertEquals(original, decodedFrom1)
      }
   }
}

