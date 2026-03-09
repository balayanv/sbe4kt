package sbe4kt.lib

import org.agrona.DirectBuffer
import org.agrona.MutableDirectBuffer
import org.agrona.concurrent.UnsafeBuffer

// codecs

object ByteArrayCodec {
   const val BYTE_SIZE = 1

   fun get(buffer: DirectBuffer, offset: Int, index: Int): Byte = buffer.getByte(offset + index)
   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: Byte) = apply { buffer.putByte(offset + index, value) }

   fun encodeFrom(source: ByteArray, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
      buffer.putBytes(offset, source, from, length)
   }

   fun decodeInto(target: ByteArray, from: Int, length: Int, buffer: DirectBuffer, offset: Int) {
      buffer.getBytes(offset, target, from, length)
   }

   fun size(count: Int): Int = count * BYTE_SIZE
}

object ShortArrayCodec {
   const val SHORT_SIZE = 2

   fun get(buffer: DirectBuffer, offset: Int, index: Int): Short = buffer.getShort(offset + index * 2)
   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: Short) = apply { buffer.putShort(offset + index * 2, value) }

   fun encodeFrom(source: ShortArray, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
      for (i in 0 until length) set(buffer, offset, i, source[from + i])
   }

   fun decodeInto(target: ShortArray, from: Int, length: Int, buffer: DirectBuffer, offset: Int) {
      for (i in 0 until length) target[from + i] = get(buffer, offset, i)
   }

   fun size(count: Int): Int = count * SHORT_SIZE
}

object IntArrayCodec {
   const val INT_SIZE = 4

   fun get(buffer: DirectBuffer, offset: Int, index: Int): Int = buffer.getInt(offset + index * 4)
   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: Int) = apply { buffer.putInt(offset + index * 4, value) }

   fun encodeFrom(source: IntArray, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
      for (i in 0 until length) set(buffer, offset, i, source[from + i])
   }

   fun decodeInto(target: IntArray, from: Int, length: Int, buffer: DirectBuffer, offset: Int) {
      for (i in 0 until length) target[from + i] = get(buffer, offset, i)
   }

   fun size(count: Int): Int = count * INT_SIZE
}

object LongArrayCodec {
   const val LONG_SIZE = 8

   fun get(buffer: DirectBuffer, offset: Int, index: Int): Long = buffer.getLong(offset + index * 8)
   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: Long) = apply { buffer.putLong(offset + index * 8, value) }

   fun encodeFrom(source: LongArray, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
      for (i in 0 until length) set(buffer, offset, i, source[from + i])
   }

   fun decodeInto(target: LongArray, from: Int, length: Int, buffer: DirectBuffer, offset: Int) {
      for (i in 0 until length) target[from + i] = get(buffer, offset, i)
   }

   fun size(count: Int): Int = count * LONG_SIZE
}

object FloatArrayCodec {
   const val FLOAT_SIZE = 4

   fun get(buffer: DirectBuffer, offset: Int, index: Int): Float = buffer.getFloat(offset + index * 4)
   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: Float) = apply { buffer.putFloat(offset + index * 4, value) }

   fun encodeFrom(source: FloatArray, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
      for (i in 0 until length) set(buffer, offset, i, source[from + i])
   }

   fun decodeInto(target: FloatArray, from: Int, length: Int, buffer: DirectBuffer, offset: Int) {
      for (i in 0 until length) target[from + i] = get(buffer, offset, i)
   }

   fun size(count: Int): Int = count * FLOAT_SIZE
}

object DoubleArrayCodec {
   const val DOUBLE_SIZE = 8

   fun get(buffer: DirectBuffer, offset: Int, index: Int): Double = buffer.getDouble(offset + index * 8)
   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: Double) = apply { buffer.putDouble(offset + index * 8, value) }

   fun encodeFrom(source: DoubleArray, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
      for (i in 0 until length) set(buffer, offset, i, source[from + i])
   }

   fun decodeInto(target: DoubleArray, from: Int, length: Int, buffer: DirectBuffer, offset: Int) {
      for (i in 0 until length) target[from + i] = get(buffer, offset, i)
   }

   fun size(count: Int): Int = count * DOUBLE_SIZE
}

object CharArrayCodec {
   const val CHAR_SIZE = 1

   fun get(buffer: DirectBuffer, offset: Int, index: Int): Char = buffer.getByte(offset + index).toInt().toChar()
   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: Char) = apply { buffer.putByte(offset + index, value.code.toByte()) }
   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: Byte) = apply { buffer.putByte(offset + index, value) }

   fun encodeFrom(source: CharArray, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
      for (i in 0 until length) set(buffer, offset, i, source[from + i])
   }

   fun decodeInto(target: CharArray, from: Int, length: Int, buffer: DirectBuffer, offset: Int) {
      for (i in 0 until length) target[from + i] = get(buffer, offset, i)
   }

   fun encodeFromCharSequence(source: CharSequence, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
      for (i in 0 until length) set(buffer, offset, i, source[from + i])
   }

   fun decodeIntoAppendable(target : Appendable, length: Int, buffer: DirectBuffer, offset: Int) {
      for (i in 0 until length) {
         val b = get(buffer, offset, i)
         if (b == '\u0000') break
         target.append(b)
      }
   }

   fun size(count: Int): Int = count * CHAR_SIZE
}

// flyweights

class ByteArrayFW(
   val size: Int,
   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray(ByteArrayCodec.size(size))),
   var offset: Int = 0,
) {
   fun get(index: Int): Byte = ByteArrayCodec.get(buffer, offset, index)
   fun set(index: Int, value: Byte) = apply { ByteArrayCodec.set(buffer, offset, index, value) }

   fun encodeFrom(source: ByteArray) = apply { ByteArrayCodec.encodeFrom(source, 0, branchlessMin(source.size, size), buffer, offset) }
   fun encodeFrom(source: ByteArray, from: Int, to: Int) = apply { ByteArrayCodec.encodeFrom(source, from, branchlessMin(to - from, size), buffer, offset) }
   fun decodeInto(target: ByteArray) = apply { ByteArrayCodec.decodeInto(target, 0, branchlessMin(target.size, size), buffer, offset) }
   fun decodeInto(target: ByteArray, from: Int, to: Int) = apply { ByteArrayCodec.decodeInto(target, from, branchlessMin(to - from, size), buffer, offset) }
}

class ShortArrayFW(
   val size: Int,
   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray(ShortArrayCodec.size(size))),
   var offset: Int = 0,
) {
   fun get(index: Int): Short = ShortArrayCodec.get(buffer, offset, index)
   fun set(index: Int, value: Short) = apply { ShortArrayCodec.set(buffer, offset, index, value) }

   fun encodeFrom(source: ShortArray) = apply { ShortArrayCodec.encodeFrom(source, 0, branchlessMin(source.size, size), buffer, offset) }
   fun encodeFrom(source: ShortArray, from: Int, to: Int) = apply { ShortArrayCodec.encodeFrom(source, from, branchlessMin(to - from, size), buffer, offset) }
   fun decodeInto(target: ShortArray) = apply { ShortArrayCodec.decodeInto(target, 0, branchlessMin(target.size, size), buffer, offset) }
   fun decodeInto(target: ShortArray, from: Int, to: Int) = apply { ShortArrayCodec.decodeInto(target, from, branchlessMin(to - from, size), buffer, offset) }
}

class IntArrayFW(
   val size: Int,
   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray(IntArrayCodec.size(size))),
   var offset: Int = 0,
) {
   fun get(index: Int): Int = IntArrayCodec.get(buffer, offset, index)
   fun set(index: Int, value: Int) = apply { IntArrayCodec.set(buffer, offset, index, value) }

   fun encodeFrom(source: IntArray) = apply { IntArrayCodec.encodeFrom(source, 0, branchlessMin(source.size, size), buffer, offset) }
   fun encodeFrom(source: IntArray, from: Int, to: Int) = apply { IntArrayCodec.encodeFrom(source, from, branchlessMin(to - from, size), buffer, offset) }
   fun decodeInto(target: IntArray) = apply { IntArrayCodec.decodeInto(target, 0, branchlessMin(target.size, size), buffer, offset) }
   fun decodeInto(target: IntArray, from: Int, to: Int) = apply { IntArrayCodec.decodeInto(target, from, branchlessMin(to - from, size), buffer, offset) }
}

class LongArrayFW(
   val size: Int,
   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray(LongArrayCodec.size(size))),
   var offset: Int = 0,
) {
   fun get(index: Int): Long = LongArrayCodec.get(buffer, offset, index)
   fun set(index: Int, value: Long) = apply { LongArrayCodec.set(buffer, offset, index, value) }

   fun encodeFrom(source: LongArray) = apply { LongArrayCodec.encodeFrom(source, 0, branchlessMin(source.size, size), buffer, offset) }
   fun encodeFrom(source: LongArray, from: Int, to: Int) = apply { LongArrayCodec.encodeFrom(source, from, branchlessMin(to - from, size), buffer, offset) }
   fun decodeInto(target: LongArray) = apply { LongArrayCodec.decodeInto(target, 0, branchlessMin(target.size, size), buffer, offset) }
   fun decodeInto(target: LongArray, from: Int, to: Int) = apply { LongArrayCodec.decodeInto(target, from, branchlessMin(to - from, size), buffer, offset) }
}

class FloatArrayFW(
   val size: Int,
   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray(FloatArrayCodec.size(size))),
   var offset: Int = 0,
) {
   fun get(index: Int): Float = FloatArrayCodec.get(buffer, offset, index)
   fun set(index: Int, value: Float) = apply { FloatArrayCodec.set(buffer, offset, index, value) }

   fun encodeFrom(source: FloatArray) = apply { FloatArrayCodec.encodeFrom(source, 0, branchlessMin(source.size, size), buffer, offset) }
   fun encodeFrom(source: FloatArray, from: Int, to: Int) = apply { FloatArrayCodec.encodeFrom(source, from, branchlessMin(to - from, size), buffer, offset) }
   fun decodeInto(target: FloatArray) = apply { FloatArrayCodec.decodeInto(target, 0, branchlessMin(target.size, size), buffer, offset) }
   fun decodeInto(target: FloatArray, from: Int, to: Int) = apply { FloatArrayCodec.decodeInto(target, from, branchlessMin(to - from, size), buffer, offset) }
}

class DoubleArrayFW(
   val size: Int,
   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray(DoubleArrayCodec.size(size))),
   var offset: Int = 0,
) {
   fun get(index: Int): Double = DoubleArrayCodec.get(buffer, offset, index)
   fun set(index: Int, value: Double) = apply { DoubleArrayCodec.set(buffer, offset, index, value) }

   fun encodeFrom(source: DoubleArray) = apply { DoubleArrayCodec.encodeFrom(source, 0, branchlessMin(source.size, size), buffer, offset) }
   fun encodeFrom(source: DoubleArray, from: Int, to: Int) = apply { DoubleArrayCodec.encodeFrom(source, from, branchlessMin(to - from, size), buffer, offset) }
   fun decodeInto(target: DoubleArray) = apply { DoubleArrayCodec.decodeInto(target, 0, branchlessMin(target.size, size), buffer, offset) }
   fun decodeInto(target: DoubleArray, from: Int, to: Int) = apply { DoubleArrayCodec.decodeInto(target, from, branchlessMin(to - from, size), buffer, offset) }
}

class CharArrayFW(
   val size: Int,
   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray(CharArrayCodec.size(size))),
   var offset: Int = 0,
) : CharSequence {
   override fun get(index: Int): Char = CharArrayCodec.get(buffer, offset, index)
   fun set(index: Int, value: Char) = apply { CharArrayCodec.set(buffer, offset, index, value) }
   fun set(index: Int, value: Byte) = apply { CharArrayCodec.set(buffer, offset, index, value) }

   fun encodeFrom(source: CharArray) = apply { CharArrayCodec.encodeFrom(source, 0, branchlessMin(source.size, size), buffer, offset) }
   fun encodeFrom(source: CharArray, from: Int, to: Int) = apply {
      val len = branchlessMin(to - from, size)
      CharArrayCodec.encodeFrom(source, from, len, buffer, offset)
      if (len < size) buffer.putByte(offset + len, 0)
   }

   fun decodeInto(target: CharArray) = apply { CharArrayCodec.decodeInto(target, 0, branchlessMin(target.size, size), buffer, offset) }
   fun decodeInto(target: CharArray, from: Int, to: Int) = apply { CharArrayCodec.decodeInto(target, from, branchlessMin(to - from, size), buffer, offset) }

   fun encodeFromCharSequence(source: CharSequence) = apply {
      val len = branchlessMin(source.length, size)
      CharArrayCodec.encodeFromCharSequence(source, 0, len, buffer, offset)
      if (len < size) buffer.putByte(offset + len, 0)
   }
   fun encodeFromCharSequence(source: CharSequence, from: Int, to: Int) = apply {
      val len = branchlessMin(to - from, size)
      CharArrayCodec.encodeFromCharSequence(source, from, len, buffer, offset)
      if (len < size) buffer.putByte(offset + len, 0)
   }
   fun decodeIntoAppendable(target: Appendable) = apply {
      CharArrayCodec.decodeIntoAppendable(target, size, buffer, offset)
   }

   override val length: Int get() = size
   override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = StringBuilder(endIndex - startIndex).apply { for (i in startIndex until endIndex) append(get(i)) }
   override fun toString(): String = StringBuilder(size).also { CharArrayCodec.decodeIntoAppendable(it, size, buffer, offset) }.toString()
}

class ArrayFW<T>(
   private val count: Int,
   private val stride: Int,
   private val wrapElement: (DirectBuffer, Int, Int) -> T,
   val buffer: UnsafeBuffer = UnsafeBuffer(),
   var offset: Int = 0,
) {
   operator fun get(index: Int): T {
      if (index !in 0..<count) throw IndexOutOfBoundsException("index $index out of bounds for array[$count]")
      return wrapElement(buffer, offset + index * stride, stride)
   }

   val size: Int get() = count
}
