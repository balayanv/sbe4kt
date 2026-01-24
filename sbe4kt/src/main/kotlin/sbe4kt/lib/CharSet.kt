package sbe4kt.lib

import kotlin.math.ceil
import kotlin.math.log2
import kotlin.random.Random

/**
 * Null terminated charset base.
 */
abstract class CharSet(val chars: String, val limit: Int = 128) {
   val bitWidth: Int = ceil(log2((chars.length + 1).toDouble())).toInt()
   private val lookup = IntArray(limit) { -1 }

   init {
      require(chars.isNotEmpty()) { "Alphabet cannot be empty" }
      require(chars.length == chars.toSet().size) { "Duplicates not allowed" }

      // pre-compute the indices
      for (i in chars.indices) {
         val code = chars[i].code
         require(code < limit) { "char '${chars[i]}' ($code) is out of lookup table range ($limit)." }
         lookup[code] = i
      }
   }

   /**
    * Return index of this character in this CharSet or -1 if character is not in the set.
    */
   fun getIndexOf(c: Char): Int {
      val code = c.code
      if (code !in 0..<limit) return -1
      return lookup[code]
   }

   operator fun get(index: Int): Char = chars[index]

   override fun toString() = "CharSet(chars='$chars')"
}