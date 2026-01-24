@file:Suppress("unused")

package sbe4kt.lib

import java.util.concurrent.ThreadLocalRandom

@JvmInline
value class Nanos(val nanos: Long) : Comparable<Nanos> {
   fun toUs() = Micros(this.nanos / 1_000L)
   fun toMs() = Millis(this.nanos / 1_000_000L)
   fun toS() = Seconds(this.nanos / 1_000_000_000L)

   operator fun plus(other: Nanos) = Nanos(this.nanos + other.nanos)
   operator fun minus(other: Nanos) = Nanos(this.nanos - other.nanos)
   override fun compareTo(other: Nanos) = this.nanos.compareTo(other.nanos)
   fun toDebugString() = "${this.nanos}ns"
}

@JvmInline
value class Micros(val micros: Long) : Comparable<Micros> {
   fun toNs() = Nanos(micros * 1_000L)
   fun toMs() = Millis(micros / 1_000L)
   fun toS() = Seconds(micros / 1_000_000L)

   operator fun plus(other: Micros) = Micros(this.micros + other.micros)
   operator fun minus(other: Micros) = Micros(this.micros - other.micros)
   override fun compareTo(other: Micros) = micros.compareTo(other.micros)
   fun toDebugString() = "${micros}µs"
}

@JvmInline
value class Millis(val millis: Long) : Comparable<Millis> {
   fun toNs() = Nanos(millis * 1_000_000L)
   fun toUs() = Micros(millis * 1_000L)
   fun toS() = Seconds(millis / 1_000L)

   operator fun plus(other: Millis) = Millis(this.millis + other.millis)
   operator fun minus(other: Millis) = Millis(this.millis - other.millis)
   override fun compareTo(other: Millis) = millis.compareTo(other.millis)
   fun toDebugString() = "${millis}ms"
}

@JvmInline
value class Seconds(val seconds: Long) : Comparable<Seconds> {
   fun toNs() = Nanos(seconds * 1_000_000_000L)
   fun toUs() = Micros(seconds * 1_000_000L)
   fun toMs() = Millis(seconds * 1_000L)

   operator fun plus(other: Seconds) = Seconds(this.seconds + other.seconds)
   operator fun minus(other: Seconds) = Seconds(this.seconds - other.seconds)
   override fun compareTo(other: Seconds) = seconds.compareTo(other.seconds)
   fun toDebugString() = "${seconds}s"
}

val Int.ns get() = Nanos(this.toLong())
val Int.us get() = Micros(this.toLong())
val Int.ms get() = Millis(this.toLong())
val Int.s get() = Seconds(this.toLong())

val Long.ns get() = Nanos(this)
val Long.us get() = Micros(this)
val Long.ms get() = Millis(this)
val Long.s get() = Seconds(this)

typealias Tag = Long

/**
 * Distributed Timestamp value class that allows you to use least significant 2 digits of nanosecond timestamp to generate,
 * timestamps unique to each service.
 *
 * You'll need a nanosecond timestamp provider (some system clock)
 * and basic functionality to take the next number if the current clock.nanos = the same timestamp.
 */
@JvmInline
value class TimeId(val value: Nanos = 0.ns) {

   companion object {
      fun now() = TimeId().ms(System.currentTimeMillis())
   }

   fun ms(): Millis = value.toMs()

   fun us(): Micros = value.toUs()

   fun ns(): Nanos = value

   fun tag(): Tag = value.nanos % 100

   /**
    * Returns new TimeId with the same timestamp but provided tag.
    */
   fun tag(tag: Tag): TimeId {
      require(tag in 0..99) { "Tag must be between 0 and 99" }
      return TimeId(value.withTag(tag))
   }

   fun ms(millis: Long) = TimeId(millis.ms.toNs().withTag(this.tag()))
   fun ms(millis: Millis) = TimeId(millis.toNs().withTag(this.tag()))

   fun us(micros: Long) = TimeId(micros.us.toNs().withTag(this.tag()))
   fun us(micros: Micros) = TimeId(micros.toNs().withTag(this.tag()))

   fun ns(nanos: Long) = TimeId(nanos.ns.withTag(this.tag()))
   fun ns(nanos: Nanos) = TimeId(nanos.withTag(this.tag()))

   fun nextMs(millis: Long) = nextNs(millis.ms.toNs())
   fun nextMs(millis: Millis) = nextNs(millis.toNs())

   fun nextUs(micros: Long) = nextNs(micros.us.toNs())
   fun nextUs(micros: Micros) = nextNs(micros.toNs())

   fun nextNs(nanos: Long) = nextNs(nanos.ns)
   fun nextNs(nanos: Nanos): TimeId {
      val nextIncrement = this.value + 100.ns
      return if (nanos > nextIncrement) TimeId(nanos.withTag(this.tag()))
      else TimeId(nextIncrement)
   }

   fun next(): TimeId {
      return TimeId(value + 100.ns)
   }

   fun add(micros: Micros): TimeId = TimeId(value + micros.toNs())
   fun add(millis: Millis): TimeId = TimeId(value + millis.toNs())

   fun debugToString(): String {
      val str = StringBuilder(toString())
      val lastIndex = str.lastIndex
      str.insert(lastIndex - 5, '-')
      str.insert(lastIndex, '(')
      str.insert(lastIndex + 3, ')')
      return str.toString()
   }

   override fun toString(): String = value.nanos.toString()

   private fun Nanos.withTag(tag: Tag): Nanos {
      val currentTag = nanos % 100
      return Nanos(nanos - currentTag + tag)
   }
}

fun main() {
   fun sleepSometimes() {
      if (ThreadLocalRandom.current().nextDouble(0.0, 1.0) < 0.01) {
         println("sleep")
         Thread.sleep(100)
      }
   }

   var id = TimeId().ms(System.currentTimeMillis()).tag(77)
   repeat(1000) {
      sleepSometimes()
      id = id.nextMs(System.currentTimeMillis())
      println(id.debugToString())
   }
}