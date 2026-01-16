package sbe4kt

import kotlin.math.pow

/**
 * A zero-allocation fixed-point decimal type packed into a single 64-bit Long.
 *
 * Layout:
 * [Scale: 8 bits] [Value: 56 bits]
 *
 * Range:
 * - Scale: 0 to 15
 * - Value: +/- 36 Quadrillion (approx +/- 3.6 * 10^16)
 *
 * From the SBE perspective, this is stored as a simple int64 field.
 */
@JvmInline
value class FPDeci(val packed: Long) : Comparable<FPDeci> {

    companion object {
        private const val SCALE_SHIFT = 56
        private const val VALUE_MASK = (1L shl SCALE_SHIFT) - 1

        // Pre-calculated powers of 10 for fast normalization
        private val POWERS_OF_10 = LongArray(16) { 10.0.pow(it).toLong() }

        /**
         * Creates an FPDeci from an unscaled value and a scale.
         * Example: from(12345, 2) -> 123.45
         */
        @JvmStatic
        fun from(unscaledValue: Long, scale: Int): FPDeci {
            require(scale in 0..15) { "Scale must be between 0 and 15" }
            val s = scale.toLong() and 0xFFL
            val v = unscaledValue and VALUE_MASK
            return FPDeci((s shl SCALE_SHIFT) or v)
        }

        /**
         * Parses a string into an FPDeci.
         * Preserves exact input precision.
         */
        @JvmStatic
        fun from(value: String): FPDeci {
            val normalized = value.trim()
            val dotIndex = normalized.indexOf('.')
            return if (dotIndex == -1) {
                from(normalized.toLong(), 0)
            } else {
                val scale = normalized.length - dotIndex - 1
                val unscaled = normalized.replace(".", "").toLong()
                from(unscaled, scale)
            }
        }

        val ZERO: FPDeci = FPDeci(0L)
    }

    val scale: Int get() = (packed ushr SCALE_SHIFT).toInt()
    val value: Long get() {
        val value = packed and VALUE_MASK
        // Sign extension for negative numbers
        return if (value and (1L shl 55) != 0L) {
            value or (0xFFL shl 56)
        } else {
            value
        }
    }

   /**
    * Normalizes both operands to the same scale for arithmetic operations.
    */
   fun normalize(newScale: Int): FPDeci {
      val currentScale = scale
      return when {
         newScale == currentScale -> this
         newScale > currentScale -> {
            val scaleDiff = newScale - currentScale
            val newValue = value * POWERS_OF_10[scaleDiff]
            from(newValue, newScale)
         }
         else -> {
            val scaleDiff = currentScale - newScale
            val newValue = value / POWERS_OF_10[scaleDiff]
            from(newValue, newScale)
         }
      }
   }

   operator fun plus(other: FPDeci): FPDeci {
      val targetScale = maxOf(this.scale, other.scale)
      val normalizedThis = this.normalize(targetScale)
      val normalizedOther = other.normalize(targetScale)
      return from(
         normalizedThis.value + normalizedOther.value,
         targetScale
      )
   }

   operator fun minus(other: FPDeci): FPDeci {
      val targetScale = maxOf(this.scale, other.scale)
      val normalizedThis = this.normalize(targetScale)
      val normalizedOther = other.normalize(targetScale)
      return from(
         normalizedThis.value - normalizedOther.value,
         targetScale
      )
   }

    override operator fun compareTo(other: FPDeci): Int {
        val targetScale = maxOf(this.scale, other.scale)
        val normalizedThis = this.normalize(targetScale)
        val normalizedOther = other.normalize(targetScale)
        return normalizedThis.value.compareTo(normalizedOther.value)
    }

   /**
    * Converts to Double for display/calculations.
    */
   fun toDouble(): Double {
      return value / POWERS_OF_10[scale].toDouble()
   }

   override fun toString(): String {
      return toDouble().toString()
   }
}
