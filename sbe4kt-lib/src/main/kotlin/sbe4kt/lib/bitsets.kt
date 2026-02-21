package sbe4kt.lib

@JvmInline
value class BitSet8(val mask: Byte = 0) {
   fun getMask(index: Int): Byte = (mask.toInt() and (1 shl index)).toByte()
   fun setBitAt(index: Int): BitSet8 = BitSet8((mask.toInt() or (1 shl index)).toByte())
   fun unSetBitAt(index: Int): BitSet8 = BitSet8((mask.toInt() and (1 shl index).inv()).toByte())
   fun toggleBitAt(index: Int): BitSet8 = BitSet8((mask.toInt() xor (1 shl index)).toByte())
   fun clear() = BitSet8(0)

   operator fun get(index: Int): Boolean = getMask(index) != 0.toByte()
   fun set(index: Int, value: Boolean): BitSet8 = if (value) setBitAt(index) else unSetBitAt(index)
   fun isEmpty(): Boolean = mask == 0.toByte()

   override fun toString(): String = "BitSet('${mask.toUByte().toString(2).padStart(8, '0')}')"
}

@JvmInline
value class BitSet16(val mask: Short = 0) {
   fun getMask(index: Int): Short = (mask.toInt() and (1 shl index)).toShort()
   fun setBitAt(index: Int): BitSet16 = BitSet16((mask.toInt() or (1 shl index)).toShort())
   fun unSetBitAt(index: Int): BitSet16 = BitSet16((mask.toInt() and (1 shl index).inv()).toShort())
   fun toggleBitAt(index: Int): BitSet16 = BitSet16((mask.toInt() xor (1 shl index)).toShort())
   fun clear() = BitSet16(0)

   operator fun get(index: Int): Boolean = getMask(index) != 0.toShort()
   fun set(index: Int, value: Boolean): BitSet16 = if (value) setBitAt(index) else unSetBitAt(index)
   fun isEmpty(): Boolean = mask == 0.toShort()

   override fun toString(): String = "BitSet('${mask.toUShort().toString(2).padStart(16, '0')}')"
}

@JvmInline
value class BitSet32(val mask: Int = 0) {
   fun getMask(index: Int): Int = mask and (1 shl index)
   fun setBitAt(index: Int): BitSet32 = BitSet32(mask or (1 shl index))
   fun unSetBitAt(index: Int): BitSet32 = BitSet32(mask and (1 shl index).inv())
   fun toggleBitAt(index: Int): BitSet32 = BitSet32(mask xor (1 shl index))
   fun clear() = BitSet32(0)

   operator fun get(index: Int): Boolean = getMask(index) != 0
   fun set(index: Int, value: Boolean): BitSet32 = if (value) setBitAt(index) else unSetBitAt(index)
   fun isEmpty(): Boolean = mask == 0

   override fun toString(): String = "BitSet('${mask.toUInt().toString(2).padStart(32, '0')}')"
}

@JvmInline
value class BitSet(val mask: Long = 0L) {
   fun getMask(index: Int): Long = mask and (1L shl index)
   fun setBitAt(index: Int): BitSet = BitSet(mask or (1L shl index))
   fun unSetBitAt(index: Int): BitSet = BitSet(mask and (1L shl index).inv())
   fun toggleBitAt(index: Int): BitSet = BitSet(mask xor (1L shl index))
   fun clear() = BitSet(0)

   operator fun get(index: Int): Boolean = getMask(index) != 0L
   fun set(index: Int, value: Boolean): BitSet = if (value) setBitAt(index) else unSetBitAt(index)
   fun isEmpty(): Boolean = mask == 0L

   override fun toString(): String = "BitSet('${mask.toBinaryString()}')"
}