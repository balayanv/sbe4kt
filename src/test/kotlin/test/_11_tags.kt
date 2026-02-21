package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.META
import sbe4kt.SBE
import sbe4kt.lib.MicroTag4
import sbe4kt.lib.TimeId
import sbe4kt.lib.us

/**
 * Some sort of diagnostic message (like heartbeat), that traces trough multiple hot path services,
 * to collect realtime telemetry data...
 */
@SBE
data class LatencyTracer(
   var created: TimeId,
   @META(tags = ["created", "MileStone1", "MileStone2", "MileStone3", "MileStone4"])
   var tags: MicroTag4,
)

class TagsTest {

   @Test
   fun recordAndRetrieveTags() {
      val baseTime = TimeId.now().tag(42)
      val tracer = newLatencyTracer(
         created = baseTime,
         tags = MicroTag4()
      )

      tracer.recordMileStone1(baseTime.add(10.us))
      tracer.recordMileStone2(baseTime.add(25.us))
      tracer.recordMileStone3(baseTime.add(40.us))
      tracer.recordMileStone4(baseTime.add(60.us))

      assertEquals(baseTime.add(10.us).ns().nanos, tracer.getTimeMileStone1().ns().nanos)
      assertEquals(baseTime.add(25.us).ns().nanos, tracer.getTimeMileStone2().ns().nanos)
      assertEquals(baseTime.add(40.us).ns().nanos, tracer.getTimeMileStone3().ns().nanos)
      assertEquals(baseTime.add(60.us).ns().nanos, tracer.getTimeMileStone4().ns().nanos)
   }

   @Test
   fun tagsRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(LatencyTracerCodec.SIZE))
      val baseTime = TimeId.now().tag(77)

      val tracer = newLatencyTracer(
         created = baseTime,
         tags = MicroTag4()
      )

      tracer.recordMileStone1(baseTime.add(5.us))
      tracer.recordMileStone2(baseTime.add(10.us))
      tracer.recordMileStone3(baseTime.add(15.us))
      tracer.recordMileStone4(baseTime.add(20.us))

      encodeLatencyTracer(tracer, buffer)

      val decoded = newLatencyTracer()
      decodeLatencyTracer(decoded, buffer)

      assertEquals(baseTime.add(5.us).ns().nanos, decoded.getTimeMileStone1().ns().nanos)
      assertEquals(baseTime.add(10.us).ns().nanos, decoded.getTimeMileStone2().ns().nanos)
      assertEquals(baseTime.add(15.us).ns().nanos, decoded.getTimeMileStone3().ns().nanos)
      assertEquals(baseTime.add(20.us).ns().nanos, decoded.getTimeMileStone4().ns().nanos)
   }

   @Test
   fun printLayout() {
      val baseTime = TimeId.now().tag(99)
      val tracer = newLatencyTracer(created = baseTime, tags = MicroTag4())
      tracer.recordMileStone1(baseTime.add(10.us))
      tracer.recordMileStone2(baseTime.add(20.us))
      tracer.recordMileStone3(baseTime.add(30.us))
      tracer.recordMileStone4(baseTime.add(50.us))
      tracer.printSbeLayout()
   }
}
