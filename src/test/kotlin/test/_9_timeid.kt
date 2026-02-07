package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE
import sbe4kt.lib.TimeId
import sbe4kt.lib.ms

@SBE
data class Msg8(var time: TimeId)

class TimeIdTest {

   @Test
   fun timeIdWithTagRoundtrips() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_MSG8))
      val timeWithTag = TimeId().ms(System.currentTimeMillis()).tag(77)

      Msg8(time = timeWithTag).encodeTo(buffer)

      val decoded = newMsg8()
      decoded.decodeFrom(buffer)

      assertEquals(77L, decoded.time.tag())
      assertEquals(timeWithTag.ms().millis, decoded.time.ms().millis)
   }

   @Test
   fun sequentialTimeIdsAreUnique() {
      var id = TimeId().ms(System.currentTimeMillis()).tag(42)
      val seen = mutableSetOf<Long>()

      repeat(100) {
         id = id.nextMs(System.currentTimeMillis())
         val nanos = id.ns().nanos
         assertTrue(seen.add(nanos), "Duplicate TimeId generated: $nanos")
      }
   }

   @Test
   fun tagIsPreservedAcrossNext() {
      val id = TimeId().ms(System.currentTimeMillis()).tag(99)
      val next = id.next()

      assertEquals(99L, next.tag())
      assertTrue(next.ns().nanos > id.ns().nanos)
   }
}