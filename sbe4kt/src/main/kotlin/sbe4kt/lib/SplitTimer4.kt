package sbe4kt.lib

/**
 * Allows to record 4 durations packed into a single Long.
 * Each slot holds a 16-bit value (0-65535).
 * Values exceeding MAX_SLOT_VALUE saturate to it, signalling overflow.
 */
@JvmInline
value class SplitTimer4(private val storage: Long = 0L) {

   companion object {
      const val MAX_SLOT_VALUE = 0xFFFF // 65,535
      private const val MASK = 0xFFFFL
   }

   fun set1(duration: Int) = set(duration, 0)
   fun set2(duration: Int) = set(duration, 16)
   fun set3(duration: Int) = set(duration, 32)
   fun set4(duration: Int) = set(duration, 48)

   private fun set(duration: Int, shift: Int): SplitTimer4 {
      val safeVal = when {
         duration < 0 -> 0L
         duration > MAX_SLOT_VALUE -> MAX_SLOT_VALUE.toLong()
         else -> duration.toLong()
      }
      val clearMask = (MASK shl shift).inv()
      return SplitTimer4((storage and clearMask) or (safeVal shl shift))
   }

   fun get1(): Int = ((storage ushr 0) and MASK).toInt()
   fun get2(): Int = ((storage ushr 16) and MASK).toInt()
   fun get3(): Int = ((storage ushr 32) and MASK).toInt()
   fun get4(): Int = ((storage ushr 48) and MASK).toInt()

   fun maxValue() = MAX_SLOT_VALUE
   fun toDebugString() =
      "binary : [${storage.toBinaryString(16)}]\nvalues : [${get1()}, ${get2()}, ${get3()}, ${get4()}]"
}

fun Long.toSplitTimer4() = SplitTimer4(this)

fun main() {
   val timer = SplitTimer4()
      .set1(11)
      .set2(23)
      .set3(Int.MAX_VALUE)
      .set4(45)

   println(timer.get1())
   println(timer.get2())
   println(timer.get3())
   println(timer.get4())

   println(timer.toDebugString())
}