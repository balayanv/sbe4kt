package sbe4kt

import sbe4kt.lib.toBinaryString

@JvmInline
value class BitSet(val mask: Long = 0L) {

    /**
     * returns true if the bit at [index] is 1, false otherwise.
     */
    operator fun get(index: Int): Boolean {
//        requireIndex(index)
        return (mask and (1L shl index)) != 0L
    }

   /**
    * Convenience method to set or unset based on a boolean [value].
    */
   fun set(index: Int, value: Boolean): BitSet {
      return if (value) set(index) else unset(index)
   }
    /**
     * Returns a new BitSet with the bit at [index] set to 1.
     */
    fun set(index: Int): BitSet {
//        requireIndex(index)
        return BitSet(mask or (1L shl index))
    }

    /**
     * Returns a new BitSet with the bit at [index] set to 0.
     */
    fun unset(index: Int): BitSet {
//        requireIndex(index)
        return BitSet(mask and (1L shl index).inv())
    }

    /**
     * Returns a new BitSet with the bit at [index] flipped.
     */
    fun flip(index: Int): BitSet {
//        requireIndex(index)
        return BitSet(mask xor (1L shl index))
    }

    /**
     * Checks if all bits are set to 0.
     */
    fun isEmpty(): Boolean = mask == 0L

    fun toBinaryString(): String = mask.toBinaryString()
    
    override fun toString(): String = "BitSet(hex=${mask.toULong().toString(16)})"

    private fun requireIndex(index: Int) {
        require(index in 0..63) { "Index must be between 0 and 63, was $index" }
    }
}

fun main() {
   var b = BitSet()
   b = b.set(5)
   b = b.set(11)
   println(b.toBinaryString())
}