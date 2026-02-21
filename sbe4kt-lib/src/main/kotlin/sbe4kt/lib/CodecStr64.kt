package sbe4kt.lib

/**
 * A high-performance packer using Bit-Shifting with Implicit Null Termination.
 * Maps characters to values `1..N`. Value `0` is reserved for "End of String".
 * Uses all 64 bits. If the string fills the capacity, no null terminator is needed.
 *
 * For example storing a "XBTUSDTH26" string (a futures symbol) you'd have these results.
 * With CodecInt64 its 8 bytes,
 * with char[] its 40 bytes,
 * with String its 64 bytes.
 *
 * CodecInt64 uses a simple long and fits nicely in a register, its 87.5% more compact,
 * and is faster to read and write to.
 *
 * Note : because 0 is the null terminator, using a charset of 2 (say "TF" for True/False)
 * can store 32 characters not 64, because bit with is 2 (we need 2 bits to represent 1 character)
 */
class CodecStr64(val charset: CharSet) {
   val maxCap: Int = 64 / charset.bitWidth
   private val mask: Long = (1L shl charset.bitWidth) - 1

   init {
      require(charset.bitWidth * maxCap <= 64) { "Configuration error: calculated width exceeds 64 bits." }
   }

   fun randomStr() = charset.randomStr(maxCap)

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
         packed = packed or (wireValue shl (i * charset.bitWidth))
      }

      return packed
   }

   /**
    * Returns the actual length of the encoded string (up to the null terminator).
    * This is crucial for CharSequence compatibility.
    */
   fun length(data: Long): Int {
      for (i in 0 until maxCap) {
         val wireValue = ((data ushr (i * charset.bitWidth)) and mask).toInt()
         if (wireValue == 0) return i  // null terminator
      }
      return maxCap  // entire capacity used (no null terminator needed)
   }

   fun decode(data: Long, output: Appendable) {
      for (i in 0 until maxCap) {
         val wireValue = ((data ushr (i * charset.bitWidth)) and mask).toInt()
         if (wireValue == 0) break  // null terminator
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

      val wireValue = ((data ushr (i * charset.bitWidth)) and mask).toInt()
      if (wireValue == 0) throw IndexOutOfBoundsException("Index $i is beyond string length")

      val charIndex = wireValue - 1
      if (charIndex >= charset.chars.length) {
         throw IllegalArgumentException("Corrupted data: Value $wireValue out of bounds for charset")
      }
      return charset[charIndex]
   }

   override fun toString() = "CodecStr64(charset=$charset, maxCap=$maxCap, bitWidth=${charset.bitWidth})"
}