package sbe4kt

import kotlin.math.ceil
import kotlin.math.log2

/**
 * For ASCII the limit is 128 (0-127).
 */
abstract class CharSet64(val chars: String, val limit: Int = 128) {
   private val lookup = IntArray(limit) { -1 }

   init {
      require(chars.isNotEmpty()) { "Alphabet cannot be empty" }
      require(chars.length == chars.toSet().size) { "Duplicates not allowed" }

      // pre-compute the indices
      for (i in chars.indices) {
         val code = chars[i].code
         require(code < limit) { "Character '${chars[i]}' ($code) is out of limit ($limit)." }
         lookup[code] = i
      }
   }

   /**
    * Optimized O(1) lookup with bounds safety.
    * Returns -1 if character is not in the set or out of bounds.
    */
   fun getIndexOf(c: Char): Int {
      val code = c.code
      if (code !in 0..<limit) return -1
      return lookup[code]
   }

   operator fun get(index: Int): Char = chars[index]
   override fun toString() = "CharSet64(chars='$chars')"
}

/**
 * A high-performance packer using Bit-Shifting with Implicit Null Termination.
 * Maps characters to values `1..N`. Value `0` is reserved for "End of String".
 * Uses all 64 bits. If the string fills the capacity, no null terminator is needed.
 *
 * For example storing a "XBTUSDTH26" string (a futures symbol) you'd have these results.
 * With StringPacker its 8 bytes, with char[] its 40 bytes, with String its 64 bytes.
 * StringPacker uses a simple long and fits nicely in a register, its 87.5% more compact,
 * and is faster to read and write to.
 */
class CodecStr64(val charset: CharSet64) {
   // We need 'alphabet.length + 1' states because 0 is reserved for EOS.
   private val bitWidth: Int = ceil(log2((charset.chars.length + 1).toDouble())).toInt()
   private val mask: Long = (1L shl bitWidth) - 1
   val maxCap: Int = 64 / bitWidth

   init {
      require(bitWidth * maxCap <= 64) { "Configuration error: calculated width exceeds 64 bits." }
   }

   /**
    * Packs the input string into a Long.
    */
   fun encode(input: CharSequence): Long {
      val len = input.length
      require(len <= maxCap) { "Input too long: $len. Max capacity is $maxCap." }

      var packed = 0L

      for (i in input.indices) {
         val charIndex = charset.getIndexOf(input[i])

         if (charIndex == -1) throw IllegalArgumentException("Invalid character '${input[i]}' (Code: ${input[i].code}) at index $i. Allowed charset: ${charset.chars}")

         // Map User Char (0..N-1) to Wire Format (1..N).
         val wireValue = (charIndex + 1).toLong()

         // Shift and OR
         packed = packed or (wireValue shl (i * bitWidth))
      }

      return packed
   }

   /**
    * Returns the actual length of the encoded string (up to the null terminator).
    * This is crucial for CharSequence compatibility.
    */
   fun length(data: Long): Int {
      for (i in 0 until maxCap) {
         val wireValue = ((data ushr (i * bitWidth)) and mask).toInt()
         if (wireValue == 0) return i  // Found null terminator
      }
      return maxCap  // String fills entire capacity (no null terminator needed)
   }

   fun decode(data: Long, output: Appendable) {
      for (i in 0 until maxCap) {
         val wireValue = ((data ushr (i * bitWidth)) and mask).toInt()
         if (wireValue == 0) break  // Stop at null terminator
         val charIndex = wireValue - 1
         if (charIndex >= charset.chars.length) {
            throw IllegalArgumentException("Corrupted data: Value $wireValue out of bounds for charset")
         }
         output.append(charset[charIndex])
      }
   }

   fun decode(data: Long): CharSequence {
      val actualLength = length(data)
      val sb = StringBuilder(actualLength)
      decode(data, sb)
      return sb.toString()
   }

   /**
    * Gets the character at the specified index.
    * Throws IndexOutOfBoundsException if index is beyond the actual string length.
    */
   fun getChar(data: Long, i: Int): Char {
      if (i < 0) throw IndexOutOfBoundsException("Index $i is negative")
      if (i >= maxCap) throw IndexOutOfBoundsException("Index $i exceeds maximum capacity $maxCap")

      val wireValue = ((data ushr (i * bitWidth)) and mask).toInt()
      if (wireValue == 0) throw IndexOutOfBoundsException("Index $i is beyond string length")

      val charIndex = wireValue - 1
      if (charIndex >= charset.chars.length) {
         throw IllegalArgumentException("Corrupted data: Value $wireValue out of bounds for charset")
      }
      return charset[charIndex]
   }

   override fun toString() = "CodecStr64(charset=$charset, maxCap=$maxCap)"
}