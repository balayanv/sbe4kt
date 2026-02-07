package sbe4kt.lib

/**
 * Allows to record 4 durations in microseconds packed into a single Long.
 * Each slot holds a 16-bit value up to 65535 microseconds or 65 milliseconds.
 * Values that are exactly MAX_SLOT_VALUE signal either recorded number was higher than max or never was set.
 */
@JvmInline
value class MicroTag4(val value: Long = 0L) {

   companion object {
      val MAX_SLOT_VALUE = 0xFFFF.us // 65,535
      private val ZERO = 0.us
      private const val MASK = 0xFFFFL
   }

   fun set1(duration: Micros) = set(duration, 0)
   fun set2(duration: Micros) = set(duration, 16)
   fun set3(duration: Micros) = set(duration, 32)
   fun set4(duration: Micros) = set(duration, 48)

   private fun set(duration: Micros, shift: Int): MicroTag4 {
      val safeVal = when {
         duration < ZERO -> ZERO
         duration > MAX_SLOT_VALUE -> MAX_SLOT_VALUE
         else -> duration
      }
      val clearMask = (MASK shl shift).inv()
      return MicroTag4((value and clearMask) or (safeVal.micros shl shift))
   }

   fun get1(): Micros = ((value ushr 0) and MASK).us
   fun get2(): Micros = ((value ushr 16) and MASK).us
   fun get3(): Micros = ((value ushr 32) and MASK).us
   fun get4(): Micros = ((value ushr 48) and MASK).us

   private fun getStr(duration : Micros) = if (duration == MAX_SLOT_VALUE) "--" else duration.micros.toString()

   override fun toString() = "[${getStr(get1())}, ${getStr(get2())}, ${getStr(get3())}, ${getStr(get4())}]"
   fun toDebugString() = "binary : [${value.toBinaryString(16)}]\nvalues : ${toString()}"
}

internal fun main() {
   val timer = MicroTag4()
      .set1(11.us)
      .set2(22.us)
//      .set3(33.us)
      .set4(45.us)

   println(timer.toDebugString())
}