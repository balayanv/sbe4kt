package sbe4kt

import sbe4kt.lib.toBinaryString

@JvmInline
value class BitSet(val mask: Long = 0L) {

   operator fun get(index: Int): Boolean = getMask(index) != 0L


   fun getMask(index: Int): Long = mask and (1L shl index)

   fun set(index: Int, value: Boolean): BitSet = if (value) set(index) else unset(index)
   fun set(index: Int): BitSet = BitSet(mask or (1L shl index))
   fun unset(index: Int): BitSet = BitSet(mask and (1L shl index).inv())
   fun flip(index: Int): BitSet = BitSet(mask xor (1L shl index))
   fun isEmpty(): Boolean = mask == 0L

   override fun toString(): String = "BitSet(hex=${mask.toULong().toString(16)})"
   fun toBinaryString(): String = mask.toBinaryString()

   fun checkIndex(index: Int) = require(index in 0..63) { "Index must be between 0 and 63, was $index" }
}

fun main() {
   var b = BitSet()
   b = b.set(5)
   b = b.set(11)
   println(b.toString())
   println(b.toBinaryString())
}