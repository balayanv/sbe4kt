@file:Suppress("unused")

package sbe4kt.lib

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

private val POWERS_OF_10 = LongArray(16) { 10.0.pow(it).toLong() }

/**
 * Utilities for hardcoded fixed-point decimal value classes.
 * Each FPD class represents a specific scale (0-15) without a common type.
 */
private object Util {

   fun fromCharSequenceExact(value: CharSequence, targetScale: Int, usableBits: Int = 64): Long {
      // Trim whitespace
      var start = 0
      var end = value.length
      while (start < end && value[start].isWhitespace()) start++
      while (end > start && value[end - 1].isWhitespace()) end--

      val len = end - start
      if (len == 0) {
         throw IllegalArgumentException("Empty string cannot be parsed")
      }

      var dotIndex = -1
      var negative = false
      var unscaled = 0L
      var i = start

      // Handle negative sign
      if (value[i] == '-') {
         negative = true
         i++
         if (i == end) throw IllegalArgumentException("Invalid numeric string: $value")
      } else if (value[i] == '+') {
         i++
         if (i == end) throw IllegalArgumentException("Invalid numeric string: $value")
      }

      // Parse digits and find decimal point
      while (i < end) {
         when (val c = value[i]) {
            '.' -> {
               if (dotIndex != -1) throw IllegalArgumentException("Multiple decimal points in: $value")
               dotIndex = i
            }

            in '0'..'9' -> {
               val digit = c - '0'
               // Check for overflow before multiplication and addition
               if (unscaled > (Long.MAX_VALUE - digit) / 10) {
                  throw ArithmeticException("Numeric overflow while parsing: $value")
               }
               unscaled = unscaled * 10 + digit
            }

            else -> throw IllegalArgumentException("Invalid character in numeric string: $value")
         }
         i++
      }

      // Check for standalone decimal point
      if (len == 1 && value[start] == '.') throw IllegalArgumentException("Single decimal point cannot be parsed")

      val scale = if (dotIndex == -1 || dotIndex == end - 1) 0 else end - dotIndex - 1

      // Adjust for target scale
      val scaleDiff = targetScale - scale
      val adjustedValue = if (scaleDiff >= 0) {
         unscaled * POWERS_OF_10[scaleDiff]
      } else {
         unscaled / POWERS_OF_10[-scaleDiff]
      }

      val finalValue = if (negative) -adjustedValue else adjustedValue

      // Check if value fits in usable bits
      if (usableBits < 64) {
         val shift = 64 - usableBits
         if ((finalValue shl shift) shr shift != finalValue) {
            throw ArithmeticException("Value $finalValue exceeds $usableBits-bit range")
         }
      }

      return finalValue
   }

   fun fromDoubleExact(value: Double, targetScale: Int, usableBits: Int = 64): Long {
      if (value.isNaN() || value.isInfinite()) {
         throw IllegalArgumentException("Cannot convert NaN or Infinite")
      }

      val scaleFactor = POWERS_OF_10[targetScale]
      val scaledDouble = value * scaleFactor
      val result = round(scaledDouble).toLong()

      // Check if value fits in usable bits
      if (usableBits < 64) {
         val shift = 64 - usableBits
         if ((result shl shift) shr shift != result) {
            throw ArithmeticException("Value $result exceeds $usableBits-bit range")
         }
      }

      return result
   }

   fun toDouble(value: Long, scale: Int): Double = value.toDouble() / POWERS_OF_10[scale]

   fun toString(value: Long, scale: Int, usableBits: Int = 64): String {
      if (scale == 0) return value.toString()

      val minValue = usableBits.minValue()
      val isNegative = value < 0
      val absValue = if (value == minValue) {
         // Special case: can't take abs of MIN_VALUE without overflow
         if (usableBits == 64) {
            // Only 64-bit Long.MIN_VALUE causes overflow
            "9223372036854775808"
         } else {
            // For smaller bit widths, fits in Long
            (1L shl (usableBits - 1)).toString()
         }
      } else {
         abs(value).toString()
      }
      val sb = StringBuilder(absValue)

      // pad with leading zeros
      while (sb.length <= scale) {
         sb.insert(0, '0')
      }

      // insert decimal point
      sb.insert(sb.length - scale, '.')

      // restore sign
      if (isNegative) {
         sb.insert(0, '-')
      }

      // Remove trailing zeros after decimal point
      val result = sb.toString()
      if (result.contains('.')) {
         var trimmed = result.trimEnd('0')
         if (trimmed.endsWith('.')) {
            trimmed = trimmed.substring(0, trimmed.length - 1)
         }
         return trimmed
      }

      return result
   }

   fun debugString(value: Long, scale: Int, usableBits: Int = 64) =
      "${toString(value, scale, usableBits)}, scale : $scale"

   fun debugStringValueRange(value: Long, scale: Int, usableBits: Int = 64): String {
      val maxValue = usableBits.maxValue()
      val minValue = usableBits.minValue()

      val min = toString(minValue, scale, usableBits)
      val max = toString(maxValue, scale, usableBits)

      return "${
         debugString(
            value,
            scale,
            usableBits
         )
      }, range : [$min, $max]${if (usableBits != 64) ", usableBits : $usableBits" else ""}"
   }

   /**
    * Converts a value from one scale to another.
    *
    * IMPORTANT: When converting to a lower scale (toScale < fromScale), precision is lost
    * through integer division (truncation toward zero). For example:
    * - 1234 at scale 3 (1.234) → scale 2 = 123 (1.23) - loses 0.004
    * - 1235 at scale 3 (1.235) → scale 2 = 123 (1.23) - loses 0.005
    *
    * @throws ArithmeticException if the conversion would overflow
    */
   fun convertScaleExact(value: Long, fromScale: Int, toScale: Int): Long {
      val scaleDiff = toScale - fromScale
      return if (scaleDiff >= 0) {
         Math.multiplyExact(value, POWERS_OF_10[scaleDiff])
      } else {
         Math.divideExact(value, POWERS_OF_10[-scaleDiff])
      }
   }

   private fun Int.maxValue() = (1L shl (this - 1)) - 1
   private fun Int.minValue() = -(1L shl (this - 1))
}

typealias FPD = PackedFixedPointDecimal

/**
 * A high-performance, zero-allocation fixed-point decimal type packed into a single 64-bit Long.
 * It only supports addition/subtraction and does not support special values like NaN or Infinity (deliberately).
 *
 * ## Memory Layout (60/4 Split)
 * The 64-bit payload is packed to maximize integer range while retaining sufficient decimal precision.
 * We utilize the full 64 bits by assigning the top 60 bits to the Value and the bottom 4 bits to the Scale.
 *
 * ```text
 * Bit Position:  63 ........................................ 4 3 2 1 0
 * Usage:         [ <---------- 60-bit Value --------------> ] [ Scale ]
 * Actual Data:   [ S V V V V V V V V V V V V V V V V V V V  ] [ S S S S ]
 * ```
 *
 * The Value is aligned with the MSB. We retrieve it with a single arithmetic shift (`SHR 4`), preserving the sign automatically.
 * Same-Scale addition/subtraction uses native CPU integer instructions plus a single bitwise correction, avoiding expensive conditional branching.
 *
 * ### Performance Characteristics
 * * **Get Value**: ~1 Instruction (`SHR 4`).
 * * **Get Scale**: ~1 Instruction (`AND 0xF`).
 * * **Same-Scale Addition**: ~6 Instructions (Branch-free logic).
 * * **Allocation**: 0 bytes (Value Class).
 *
 * ## Range and Precision
 * * **Scale**: 0 to 15 decimal places (Hard limit, fits in 4 bits).
 * * **Value**: ±576,460,752,303,423,488 (approx ±5.76 × 10^18).
 *
 * ## Cross-Scale Operations
 * When performing operations between FPD values with different scales:
 * * **Addition/Subtraction**: The value with lower scale is temporarily scaled up to match the higher scale.
 *   The result maintains the higher scale. For example: 1.23 (scale 2) + 4.567 (scale 3) = 5.797 (scale 3).
 * * **Comparison**: Values are normalized to the same scale before comparison.
 * * **Scale Conversion**: Use `withScale()` or `withScaleExact()`. Note that reducing scale truncates (rounds toward zero).
 *
 * Note : this is a value class, it compiles to a single Long, if you do .equals()
 * the same number in different scale will NOT be equal. This is expected. Instead use comparable interface.
 *
 * Methods that have 'Exact' in their name will not care about performance and will do bounds and overflow checking.
 * Use them where it makes sense, and use the unsafe methods where you are sure values are validated.
 */
@JvmInline
value class PackedFixedPointDecimal(val packed: Long) : Comparable<FPD> {

   companion object {
      private const val VALUE_SHIFT = 4
      private const val SCALE_MASK = 0xFL

      /**
       * This will be inlined, doing this solely to make the soruce more readable.
       */
      private fun Long.value() = this shr VALUE_SHIFT

      /**
       * This will be inlined, doing this solely to make the soruce more readable.
       */
      private fun Long.scale() = this and SCALE_MASK

      val ZERO = FPD(0L)

      @JvmStatic
      fun fromExact(value: Long, scale: Long): FPD {
         require(scale in 0..15) { "Scale must be between 0 and 15" }
         val packed = (value shl VALUE_SHIFT) or scale
         require(packed.value() == value) { "Value overflow: $value does not fit in 60 bits" }
         return FPD(packed)
      }

      @JvmStatic
      fun from(value: Long, scale: Int): FPD {
         return FPD((value shl VALUE_SHIFT) or scale.toLong().scale())
      }

      @JvmStatic
      fun fromExact(value: CharSequence): FPD {
         // Parse to detect scale
         var start = 0
         var end = value.length
         while (start < end && value[start].isWhitespace()) start++
         while (end > start && value[end - 1].isWhitespace()) end--

         val len = end - start
         if (len == 0) {
            throw IllegalArgumentException("Empty string cannot be parsed as PFPD")
         }

         // Quick scan to find scale (decimal places)
         var dotIndex = -1
         for (i in start until end) {
            if (value[i] == '.') {
               dotIndex = i
               break
            }
         }

         val scale = if (dotIndex == -1 || dotIndex == end - 1) 0 else end - dotIndex - 1
         require(scale <= 15) { "Scale exceeds maximum of 15: $scale" }

         // Use FPDUtils with 60-bit constraint
         val unscaledValue = Util.fromCharSequenceExact(value, scale, usableBits = 60)

         // Handle "-0" case
         if (unscaledValue == 0L) return FPD.ZERO

         return fromExact(unscaledValue, scale.toLong())
      }

      /**
       * Creates a PFPD from a Double with overflow checking.
       * @throws ArithmeticException if the conversion would overflow
       */
      @JvmStatic
      fun fromExact(value: Double, maxScale: Int): FPD {
         if (value.isNaN() || value.isInfinite()) {
            throw IllegalArgumentException("Cannot convert NaN or Infinite to PFPD")
         }

         // Check if the double is within representable range
         if (value !in -5.764607523034235E17..5.764607523034235E17) {
            throw ArithmeticException("Double value $value exceeds PFPD range")
         }

         require(maxScale in 0..15) { "MaxScale must be between 0 and 15" }

         // Use arithmetic to extract the unscaled value
         val scaleFactor = POWERS_OF_10[maxScale]
         val scaledDouble = value * scaleFactor
         val roundedLong = round(scaledDouble).toLong()

         // Check if the unscaled value fits in 60 bits
         if ((roundedLong shl VALUE_SHIFT).value() != roundedLong) {
            throw ArithmeticException("Value overflow during Double conversion")
         }

         // Calculate the actual scale by removing trailing zeros
         var actualScale = maxScale
         var unscaled = roundedLong
         while (actualScale > 0 && unscaled % 10 == 0L) {
            unscaled /= 10
            actualScale--
         }

         return fromExact(unscaled, actualScale.toLong())
      }

      @JvmStatic
      fun from(value: Double, maxScale: Int): FPD {
         // Use arithmetic to extract the unscaled value
         val scaleFactor = POWERS_OF_10[maxScale]
         val scaledDouble = value * scaleFactor
         val roundedLong = round(scaledDouble).toLong()

         // Calculate the actual scale by removing trailing zeros
         var actualScale = maxScale
         var unscaled = roundedLong
         while (actualScale > 0 && unscaled % 10 == 0L) {
            unscaled /= 10
            actualScale--
         }

         // Create the packed value without overflow checking
         return from(unscaled, actualScale)
      }
   }

   /**
    * Returns the scale (number of decimal places).
    * Cost: 1 Instruction (AND 0xF)
    */
   val scale: Long get() = packed.scale()
   val scaleInt: Int get() = packed.scale().toInt()

   /**
    * Returns the unscaled long value.
    * Cost: 1 Instruction (SHR 4)
    * Note: This automatically handles sign extension for negative numbers.
    */
   val value: Long get() = packed.value()

   /**
    * Optimized Addition.
    * Fast Path (Same Scale): ~3 Instructions.
    */
   operator fun plus(other: FPD): FPD {
      val p1 = this.packed
      val p2 = other.packed

      // check if scales are identical
      if ((p1 xor p2).scale() == 0L) {
         // direct packed addition: (v1<<4|s) + (v2<<4|s) = (v1+v2)<<4 + 2s
         // we subtract one scale to get the correct result
         return FPD(p1 + p2 - (p1.scale()))
      }

      // now we know scale is not the same (diff != 0)
      val s1 = this.scale
      val s2 = other.scale
      val diff: Int = (s1 - s2).toInt()  // scales are 0-15, so diff is -15 to 15

      if (diff > 0) {
         val v1 = this.value
         // adjust scale of the value of other
         val v2 = other.value * POWERS_OF_10[diff]
         val result = v1 + v2
         return FPD((result shl VALUE_SHIFT) or s1.scale())
      } else {
         // adjust scale of the value of this
         val v1 = this.value * POWERS_OF_10[-diff]
         val v2 = other.value
         val result = v1 + v2
         return FPD((result shl VALUE_SHIFT) or s2.scale())
      }
   }

   /**
    * Addition with overflow detection.
    * Uses optimized direct scale adjustment.
    * @throws ArithmeticException if the result overflows
    */
   fun plusExact(other: FPD): FPD {
      val p1 = this.packed
      val p2 = other.packed

      // check if scales are identical
      if ((p1 xor p2).scale() == 0L) {
         val v1 = this.value
         val v2 = other.value
         // check for addition overflow
         val result = Math.addExact(v1, v2)
         // check if the resulting value fits in 60 bits
         if ((result shl VALUE_SHIFT).value() != result) {
            throw ArithmeticException("PFPD addition overflow")
         }
         return fromExact(result, this.scale)
      }

      // Different scales - direct adjustment
      val s1 = this.scale
      val s2 = other.scale
      val diff: Int = (s1 - s2).toInt()  // scales are 0-15, so diff is -15 to 15

      try {
         if (diff > 0) {
            val v1 = this.value
            // adjust scale of the value of other - check for overflow
            val v2 = Math.multiplyExact(other.value, POWERS_OF_10[diff])
            val result = Math.addExact(v1, v2)
            // Check if the result fits in 60 bits
            if ((result shl VALUE_SHIFT).value() != result) {
               throw ArithmeticException("PFPD addition overflow")
            }
            return fromExact(result, s1)
         } else {
            // adjust scale of the value of this - check for overflow
            val v1 = Math.multiplyExact(this.value, POWERS_OF_10[-diff])
            val v2 = other.value
            val result = Math.addExact(v1, v2)
            // Check if the result fits in 60 bits
            if ((result shl VALUE_SHIFT).value() != result) {
               throw ArithmeticException("PFPD addition overflow")
            }
            return fromExact(result, s2)
         }
      } catch (e: ArithmeticException) {
         throw e
      } catch (e: IllegalArgumentException) {
         // Convert IllegalArgumentException from from() to ArithmeticException
         throw ArithmeticException("PFPD addition overflow: ${e.message}")
      }
   }

   /**
    * Optimized Subtraction.
    * Fast Path (Same Scale): ~3 Instructions.
    */
   operator fun minus(other: FPD): FPD {
      val p1 = this.packed
      val p2 = other.packed

      // check if scales are identical
      if ((p1 xor p2).scale() == 0L) {
         // direct packed subtraction, scales cancel out so we just OR it back
         return FPD((p1 - p2) or p1.scale())
      }

      // now we know scale is not the same (diff != 0)
      val s1 = this.scale
      val s2 = other.scale
      val diff: Int = (s1 - s2).toInt()  // scales are 0-15, so diff is -15 to 15

      if (diff > 0) {
         val v1 = this.value
         // adjust scale of the value of other
         val v2 = other.value * POWERS_OF_10[diff]
         val result = v1 - v2
         return FPD((result shl VALUE_SHIFT) or s1.scale())
      } else {
         // adjust scale of the value of this
         val v1 = this.value * POWERS_OF_10[-diff]
         val v2 = other.value
         val result = v1 - v2
         return FPD((result shl VALUE_SHIFT) or s2.scale())
      }
   }

   /**
    * Subtraction with overflow detection.
    * Uses optimized direct scale adjustment.
    * @throws ArithmeticException if the result overflows
    */
   fun minusExact(other: FPD): FPD {
      val p1 = this.packed
      val p2 = other.packed

      if ((p1 xor p2).scale() == 0L) {
         // Fast path: same scale
         val v1 = this.value
         val v2 = other.value
         val result = Math.subtractExact(v1, v2)
         // Check if the resulting value fits in 60 bits
         if ((result shl VALUE_SHIFT).value() != result) {
            throw ArithmeticException("PFPD subtraction overflow")
         }
         return fromExact(result, this.scale)
      }

      // Different scales - direct adjustment
      val s1 = this.scale
      val s2 = other.scale
      val diff: Int = (s1 - s2).toInt()  // scales are 0-15, so diff is -15 to 15

      try {
         if (diff > 0) {
            val v1 = this.value
            // adjust scale of the value of other - check for overflow
            val v2 = Math.multiplyExact(other.value, POWERS_OF_10[diff])
            val result = Math.subtractExact(v1, v2)
            // Check if the result fits in 60 bits
            if ((result shl VALUE_SHIFT).value() != result) {
               throw ArithmeticException("PFPD subtraction overflow")
            }
            return fromExact(result, s1)
         } else {
            // adjust scale of the value of this - check for overflow
            val v1 = Math.multiplyExact(this.value, POWERS_OF_10[-diff])
            val v2 = other.value
            val result = Math.subtractExact(v1, v2)
            // Check if the result fits in 60 bits
            if ((result shl VALUE_SHIFT).value() != result) {
               throw ArithmeticException("PFPD subtraction overflow")
            }
            return fromExact(result, s2)
         }
      } catch (e: ArithmeticException) {
         throw e
      } catch (e: IllegalArgumentException) {
         // Convert IllegalArgumentException from from() to ArithmeticException
         throw ArithmeticException("PFPD subtraction overflow: ${e.message}")
      }
   }

   /**
    * Optimized Comparison.
    * Fast Path (Same Scale): 1 Instruction (LCMP).
    */
   override operator fun compareTo(other: FPD): Int {
      val p1 = this.packed
      val p2 = other.packed

      if ((p1 xor p2).scale() == 0L) {
         return p1.compareTo(p2)
      }

      // Slow Path
      val targetScale = maxOf(this.scale, other.scale)
      val normalizedThis = this.withScale(targetScale)
      val normalizedOther = other.withScale(targetScale)
      return normalizedThis.value.compareTo(normalizedOther.value)
   }

   fun toDouble() = Util.toDouble(value, scaleInt)

   override fun toString() = Util.toString(value, scaleInt, usableBits = 60)

   fun debugToString() = Util.debugString(value, scaleInt, usableBits = 60)

   fun debugValueRange() = Util.debugStringValueRange(value, scaleInt, usableBits = 60)

   fun withScale(newScale: Long): FPD {
      val currentScale = scale
      if (newScale == currentScale) return this

      val currentValue = value
      return if (newScale > currentScale) {
         fromExact(currentValue * POWERS_OF_10[(newScale - currentScale).toInt()], newScale)
      } else {
         fromExact(currentValue / POWERS_OF_10[(currentScale - newScale).toInt()], newScale)
      }
   }

   /**
    * Rescales this PFPD to a new scale with overflow detection.
    * @throws ArithmeticException if the rescaling causes overflow
    */
   fun withScaleExact(newScale: Long): FPD {
      val currentScale = scale
      if (newScale == currentScale) return this

      val currentValue = value
      return if (newScale > currentScale) {
         val scaleFactor = POWERS_OF_10[(newScale - currentScale).toInt()]
         val result = Math.multiplyExact(currentValue, scaleFactor)
         // Check if result fits in 60 bits
         if ((result shl VALUE_SHIFT).value() != result) {
            throw ArithmeticException("Scale adjustment overflow")
         }
         try {
            fromExact(result, newScale)
         } catch (e: IllegalArgumentException) {
            // Convert IllegalArgumentException from from() to ArithmeticException
            throw ArithmeticException("Scale adjustment overflow: ${e.message}")
         }
      } else {
         fromExact(currentValue / POWERS_OF_10[(currentScale - newScale).toInt()], newScale)
      }
   }

   fun toFPD0() = FPD0(Util.convertScaleExact(value, scaleInt, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scaleInt, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scaleInt, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scaleInt, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scaleInt, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scaleInt, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scaleInt, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scaleInt, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scaleInt, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scaleInt, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scaleInt, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scaleInt, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scaleInt, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scaleInt, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scaleInt, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scaleInt, 15))
}

typealias FPD0 = FixedPointDecimal0

@JvmInline
value class FixedPointDecimal0(val value: Long) {
   companion object {
      val ZERO = FPD0(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD0(Util.fromCharSequenceExact(value, 0))

      @JvmStatic
      fun fromExact(value: Double) = FPD0(Util.fromDoubleExact(value, 0))
   }

   val scale: Int get() = 0

   operator fun plus(other: FPD0) = FPD0(value + other.value)

   fun plusExact(other: FPD0) = FPD0(Math.addExact(value, other.value))

   operator fun minus(other: FPD0) = FPD0(value - other.value)

   fun minusExact(other: FPD0) = FPD0(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD0): Int = value.compareTo(other.value)

   fun toDouble(): Double = Util.toDouble(value, scale)

   override fun toString(): String = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD1 = FixedPointDecimal1

@JvmInline
value class FixedPointDecimal1(val value: Long) {
   companion object {

      val ZERO = FPD1(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD1(Util.fromCharSequenceExact(value, 1))

      @JvmStatic
      fun fromExact(value: Double) = FPD1(Util.fromDoubleExact(value, 1))
   }

   val scale: Int get() = 1

   operator fun plus(other: FPD1) = FPD1(value + other.value)

   fun plusExact(other: FPD1) = FPD1(Math.addExact(value, other.value))

   operator fun minus(other: FPD1) = FPD1(value - other.value)

   fun minusExact(other: FPD1) = FPD1(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD1) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD2 = FixedPointDecimal2

@JvmInline
value class FixedPointDecimal2(val value: Long) {
   companion object {

      val ZERO = FPD2(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD2(Util.fromCharSequenceExact(value, 2))

      @JvmStatic
      fun fromExact(value: Double) = FPD2(Util.fromDoubleExact(value, 2))
   }

   val scale: Int get() = 2

   operator fun plus(other: FPD2) = FPD2(value + other.value)

   fun plusExact(other: FPD2) = FPD2(Math.addExact(value, other.value))

   operator fun minus(other: FPD2) = FPD2(value - other.value)

   fun minusExact(other: FPD2) = FPD2(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD2) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD3 = FixedPointDecimal3

@JvmInline
value class FixedPointDecimal3(val value: Long) {
   companion object {

      val ZERO = FPD3(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD3(Util.fromCharSequenceExact(value, 3))

      @JvmStatic
      fun fromExact(value: Double) = FPD3(Util.fromDoubleExact(value, 3))
   }

   val scale: Int get() = 3

   operator fun plus(other: FPD3) = FPD3(value + other.value)

   fun plusExact(other: FPD3) = FPD3(Math.addExact(value, other.value))

   operator fun minus(other: FPD3) = FPD3(value - other.value)

   fun minusExact(other: FPD3) = FPD3(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD3) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD4 = FixedPointDecimal4

@JvmInline
value class FixedPointDecimal4(val value: Long) {
   companion object {

      val ZERO = FPD4(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD4(Util.fromCharSequenceExact(value, 4))

      @JvmStatic
      fun fromExact(value: Double) = FPD4(Util.fromDoubleExact(value, 4))
   }

   val scale: Int get() = 4

   operator fun plus(other: FPD4) = FPD4(value + other.value)

   fun plusExact(other: FPD4) = FPD4(Math.addExact(value, other.value))

   operator fun minus(other: FPD4) = FPD4(value - other.value)

   fun minusExact(other: FPD4) = FPD4(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD4) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD5 = FixedPointDecimal5

@JvmInline
value class FixedPointDecimal5(val value: Long) {
   companion object {

      val ZERO = FPD5(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD5(Util.fromCharSequenceExact(value, 5))

      @JvmStatic
      fun fromExact(value: Double) = FPD5(Util.fromDoubleExact(value, 5))
   }

   val scale: Int get() = 5

   operator fun plus(other: FPD5) = FPD5(value + other.value)

   fun plusExact(other: FPD5) = FPD5(Math.addExact(value, other.value))

   operator fun minus(other: FPD5) = FPD5(value - other.value)

   fun minusExact(other: FPD5) = FPD5(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD5) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD6 = FixedPointDecimal6

@JvmInline
value class FixedPointDecimal6(val value: Long) {
   companion object {

      val ZERO = FPD6(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD6(Util.fromCharSequenceExact(value, 6))

      @JvmStatic
      fun fromExact(value: Double) = FPD6(Util.fromDoubleExact(value, 6))
   }

   val scale: Int get() = 6

   operator fun plus(other: FPD6) = FPD6(value + other.value)

   fun plusExact(other: FPD6) = FPD6(Math.addExact(value, other.value))

   operator fun minus(other: FPD6) = FPD6(value - other.value)

   fun minusExact(other: FPD6) = FPD6(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD6) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD7 = FixedPointDecimal7

@JvmInline
value class FixedPointDecimal7(val value: Long) {
   companion object {

      val ZERO = FPD7(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD7(Util.fromCharSequenceExact(value, 7))

      @JvmStatic
      fun fromExact(value: Double) = FPD7(Util.fromDoubleExact(value, 7))
   }

   val scale: Int get() = 7

   operator fun plus(other: FPD7) = FPD7(value + other.value)

   fun plusExact(other: FPD7) = FPD7(Math.addExact(value, other.value))

   operator fun minus(other: FPD7) = FPD7(value - other.value)

   fun minusExact(other: FPD7) = FPD7(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD7) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD8 = FixedPointDecimal8

@JvmInline
value class FixedPointDecimal8(val value: Long) {
   companion object {

      val ZERO = FPD8(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD8(Util.fromCharSequenceExact(value, 8))

      @JvmStatic
      fun fromExact(value: Double) = FPD8(Util.fromDoubleExact(value, 8))
   }

   val scale: Int get() = 8

   operator fun plus(other: FPD8) = FPD8(value + other.value)

   fun plusExact(other: FPD8) = FPD8(Math.addExact(value, other.value))

   operator fun minus(other: FPD8) = FPD8(value - other.value)

   fun minusExact(other: FPD8) = FPD8(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD8) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD9 = FixedPointDecimal9

@JvmInline
value class FixedPointDecimal9(val value: Long) {
   companion object {

      val ZERO = FPD9(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD9(Util.fromCharSequenceExact(value, 9))

      @JvmStatic
      fun fromExact(value: Double) = FPD9(Util.fromDoubleExact(value, 9))
   }

   val scale: Int get() = 9

   operator fun plus(other: FPD9) = FPD9(value + other.value)

   fun plusExact(other: FPD9) = FPD9(Math.addExact(value, other.value))

   operator fun minus(other: FPD9) = FPD9(value - other.value)

   fun minusExact(other: FPD9) = FPD9(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD9) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD10 = FixedPointDecimal10

@JvmInline
value class FixedPointDecimal10(val value: Long) {
   companion object {

      val ZERO = FPD10(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD10(Util.fromCharSequenceExact(value, 10))

      @JvmStatic
      fun fromExact(value: Double) = FPD10(Util.fromDoubleExact(value, 10))
   }

   val scale: Int get() = 10

   operator fun plus(other: FPD10) = FPD10(value + other.value)

   fun plusExact(other: FPD10) = FPD10(Math.addExact(value, other.value))

   operator fun minus(other: FPD10) = FPD10(value - other.value)

   fun minusExact(other: FPD10) = FPD10(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD10) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD11 = FixedPointDecimal11

@JvmInline
value class FixedPointDecimal11(val value: Long) {
   companion object {

      val ZERO = FPD11(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD11(Util.fromCharSequenceExact(value, 11))

      @JvmStatic
      fun fromExact(value: Double) = FPD11(Util.fromDoubleExact(value, 11))
   }

   val scale: Int get() = 11

   operator fun plus(other: FPD11) = FPD11(value + other.value)

   fun plusExact(other: FPD11) = FPD11(Math.addExact(value, other.value))

   operator fun minus(other: FPD11) = FPD11(value - other.value)

   fun minusExact(other: FPD11) = FPD11(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD11) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD12 = FixedPointDecimal12

@JvmInline
value class FixedPointDecimal12(val value: Long) {
   companion object {

      val ZERO = FPD12(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD12(Util.fromCharSequenceExact(value, 12))

      @JvmStatic
      fun fromExact(value: Double) = FPD12(Util.fromDoubleExact(value, 12))
   }

   val scale: Int get() = 12

   operator fun plus(other: FPD12) = FPD12(value + other.value)

   fun plusExact(other: FPD12) = FPD12(Math.addExact(value, other.value))

   operator fun minus(other: FPD12) = FPD12(value - other.value)

   fun minusExact(other: FPD12) = FPD12(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD12) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD13 = FixedPointDecimal13

@JvmInline
value class FixedPointDecimal13(val value: Long) {
   companion object {

      val ZERO = FPD13(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD13(Util.fromCharSequenceExact(value, 13))

      @JvmStatic
      fun fromExact(value: Double) = FPD13(Util.fromDoubleExact(value, 13))
   }

   val scale: Int get() = 13

   operator fun plus(other: FPD13) = FPD13(value + other.value)

   fun plusExact(other: FPD13) = FPD13(Math.addExact(value, other.value))

   operator fun minus(other: FPD13) = FPD13(value - other.value)

   fun minusExact(other: FPD13) = FPD13(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD13) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD14 = FixedPointDecimal14

@JvmInline
value class FixedPointDecimal14(val value: Long) {
   companion object {

      val ZERO = FPD14(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD14(Util.fromCharSequenceExact(value, 14))

      @JvmStatic
      fun fromExact(value: Double) = FPD14(Util.fromDoubleExact(value, 14))
   }

   val scale: Int get() = 14

   operator fun plus(other: FPD14) = FPD14(value + other.value)

   fun plusExact(other: FPD14) = FPD14(Math.addExact(value, other.value))

   operator fun minus(other: FPD14) = FPD14(value - other.value)

   fun minusExact(other: FPD14) = FPD14(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD14) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD15() = FPD15(Util.convertScaleExact(value, scale, 15))
}

typealias FPD15 = FixedPointDecimal15

@JvmInline
value class FixedPointDecimal15(val value: Long) {
   companion object {

      val ZERO = FPD15(0L)

      @JvmStatic
      fun fromExact(value: CharSequence) = FPD15(Util.fromCharSequenceExact(value, 15))

      @JvmStatic
      fun fromExact(value: Double) = FPD15(Util.fromDoubleExact(value, 15))
   }

   val scale: Int get() = 15

   operator fun plus(other: FPD15) = FPD15(value + other.value)

   fun plusExact(other: FPD15) = FPD15(Math.addExact(value, other.value))

   operator fun minus(other: FPD15) = FPD15(value - other.value)

   fun minusExact(other: FPD15) = FPD15(Math.subtractExact(value, other.value))

   fun compareTo(other: FPD15) = value.compareTo(other.value)

   fun toDouble() = Util.toDouble(value, scale)

   override fun toString() = Util.toString(value, scale)

   fun debugToString() = Util.debugString(value, scale)

   fun debugValueRange() = Util.debugStringValueRange(value, scale)

   fun toFPD() = FPD.from(value, scale)
   fun toFPD0() = FPD0(Util.convertScaleExact(value, scale, 0))
   fun toFPD1() = FPD1(Util.convertScaleExact(value, scale, 1))
   fun toFPD2() = FPD2(Util.convertScaleExact(value, scale, 2))
   fun toFPD3() = FPD3(Util.convertScaleExact(value, scale, 3))
   fun toFPD4() = FPD4(Util.convertScaleExact(value, scale, 4))
   fun toFPD5() = FPD5(Util.convertScaleExact(value, scale, 5))
   fun toFPD6() = FPD6(Util.convertScaleExact(value, scale, 6))
   fun toFPD7() = FPD7(Util.convertScaleExact(value, scale, 7))
   fun toFPD8() = FPD8(Util.convertScaleExact(value, scale, 8))
   fun toFPD9() = FPD9(Util.convertScaleExact(value, scale, 9))
   fun toFPD10() = FPD10(Util.convertScaleExact(value, scale, 10))
   fun toFPD11() = FPD11(Util.convertScaleExact(value, scale, 11))
   fun toFPD12() = FPD12(Util.convertScaleExact(value, scale, 12))
   fun toFPD13() = FPD13(Util.convertScaleExact(value, scale, 13))
   fun toFPD14() = FPD14(Util.convertScaleExact(value, scale, 14))
}

fun main() {
   val a = FPD.fromExact(489.2433333333333, 3)
//   val a = PFPD.fromExact(489.243, 3)
//   val b = PFPD.fromExact("3.4").withScale(a.scale)
   val b = FPD.fromExact("3.4")
   val c: FPD = a + b

   println(c.debugValueRange())
   println(c.toFPD3().debugValueRange())
   println(c.toFPD14().debugValueRange())
   println(c.withScale(15).debugValueRange())
   println(c.toFPD15().debugValueRange())

   println("---------------------------------------------")



   val fpD3 = FPD3(42000)
   println(fpD3.debugToString())
   val fpD2 = FPD2(4200)
   println(fpD2.debugToString())
//   fpD2 + fpd3 // compile error, cannot mix two different FPD types
   println("---------------------------------------------")
   val f1 = fpD3.toFPD()
   val f2 = fpD2.toFPD()
   println(f1.debugToString())
   println(f2.debugToString())
   println((f1 + f2).debugToString()) // fine because FPD is self contained and can shift scale...
   println("---------------------------------------------")


}