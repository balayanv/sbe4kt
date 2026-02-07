package sandbox

import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.dsl.Disruptor
import java.util.concurrent.ThreadFactory

class Event {
   var value: Long = 0L
   var afterStage1: Long = 0L
   var afterStage2: Long = 0L
}

class Stage1 : EventHandler<Event> {
   override fun onEvent(event: Event, sequence: Long, endOfBatch: Boolean) {
      event.afterStage1 = event.value * 2
      println("[Stage1] seq=$sequence  ${event.value} * 2 = ${event.afterStage1}")
   }
}

class Stage2 : EventHandler<Event> {
   override fun onEvent(event: Event, sequence: Long, endOfBatch: Boolean) {
      event.afterStage2 = event.afterStage1 + 100
      println("[Stage2] seq=$sequence  ${event.afterStage1} + 100 = ${event.afterStage2}")
   }
}

class Stage3 : EventHandler<Event> {
   override fun onEvent(event: Event, sequence: Long, endOfBatch: Boolean) {
      println("[Stage3] seq=$sequence  final = ${event.afterStage2}")
   }
}

fun main() {
   val disruptor = Disruptor(
      com.lmax.disruptor.EventFactory<Event> { Event() },
      1024,
      ThreadFactory { r -> Thread(r, "disruptor-worker").apply { isDaemon = true } },
   )

   disruptor
      .handleEventsWith(Stage1())
      .then(Stage2())
      .then(Stage3())

   val ringBuffer = disruptor.start()

   repeat(5) { i ->
      val seq = ringBuffer.next()
      ringBuffer.get(seq).value = i.toLong()
      ringBuffer.publish(seq)
   }

   disruptor.shutdown()
}
