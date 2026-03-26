package sbe4kt.lib

import org.agrona.DirectBuffer
import org.agrona.MutableDirectBuffer
import org.agrona.concurrent.UnsafeBuffer


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

private const val LENGTH_SIZE = 2
private const val LENGTH_MAX_VALUE = 65535

/**
 * Variable length byte[] reader.
 */
class VarByteArrayFW(
   val buffer: UnsafeBuffer,
) {
   private var offset: Int = 0
   private var dataOffset: Int = offset + LENGTH_SIZE
   private var readPos = offset + 1
   private var writePos = readPos

   fun setOffset(offset: Int) = apply {
      this.offset = offset
      this.dataOffset = offset + LENGTH_SIZE
      reset()
   }

   /**
    * Must be called before incremental reads or writes.
    * Can be skipped if setOffset() was called right before.
    */
   fun reset() {
      val pos = offset + LENGTH_SIZE - 1
      readPos = pos
      writePos = pos
   }

   /**
    * Size in bytes.
    */
   fun size(): Int = LENGTH_SIZE + ByteArrayCodec.size(length())

   /**
    * Record unsigned 2 byte size at offset.
    */
   fun size(size: Int) {
      buffer.putShort(offset, size.toUShort().toShort())
   }

   /**
    * Number of elements in this Array.
    */
   fun length(): Int = buffer.getShort(offset).toInt()

   fun read(): Byte = buffer.getByte(++readPos)
   fun write(value: Byte) = apply { buffer.putByte(++writePos, value) }

   fun endWrite(): Int {
      val size = writePos - offset - 1
      buffer.putShort(offset, size.toShort())
      return size
   }


   fun decodeInto(target: ByteArray): Int {
      val size = length()
      val length = minOf(size, target.size)
      ByteArrayCodec.decodeInto(target, 0, length, buffer, dataOffset)
      return length
   }

   fun decodeInto(target: ByteArray, from: Int, to: Int): Int {
      val size = length()
      val length = minOf(to - from, size)
      ByteArrayCodec.decodeInto(target, from, length, buffer, dataOffset)
      return size
   }

   fun encodeFrom(source: ByteArray): Int {
      val length = minOf(source.size, LENGTH_MAX_VALUE)
      buffer.putShort(offset, length.toShort())
      ByteArrayCodec.encodeFrom(source, 0, length, buffer, dataOffset)
      return length
   }

   fun encodeFrom(source: ByteArray, from: Int = 0, to: Int = source.size): Int {
      val length = minOf(to - from, LENGTH_MAX_VALUE)
      buffer.putShort(offset, length.toShort())
      ByteArrayCodec.encodeFrom(source, from, length, buffer, dataOffset)
      return length
   }
}


fun main() {
   val buffer = UnsafeBuffer(ByteArray(256))

   // --- bulk write then bulk read ---
   val source = byteArrayOf(10, 20, 30, 40, 50)
   val writer = VarByteArrayFW(buffer).setOffset(0)
   writer.encodeFrom(source)

   val reader = VarByteArrayFW(buffer).setOffset(0)
   val target = ByteArray(5)
   val len = reader.decodeInto(target)
   check(len == 5) { "expected length 5, got $len" }
   check(target.contentEquals(source)) { "bulk round-trip failed: ${target.contentToString()}" }
   println("PASS: bulk encode/decode")

   // --- bulk write then incremental read ---
   val reader2 = VarByteArrayFW(buffer).setOffset(0)
   reader2.reset()
   for (i in source.indices) {
      val b = reader2.read()
      check(b == source[i]) { "incremental read[$i]: expected ${source[i]}, got $b" }
   }
   println("PASS: bulk write -> incremental read")

   // --- incremental write then bulk read ---
   val buffer2 = UnsafeBuffer(ByteArray(256))
   val writer2 = VarByteArrayFW(buffer2).setOffset(0)
   writer2.reset()
   writer2.write(1).write(2).write(3)
   writer2.endWrite()

   val reader3 = VarByteArrayFW(buffer2).setOffset(0)
   val stored = reader3.decodeInto(ByteArray(10))
   check(stored == 3) { "expected stored length 3, got $stored" }
   val target3 = ByteArray(3)
   reader3.decodeInto(target3)
   check(target3.contentEquals(byteArrayOf(1, 2, 3))) { "incremental write round-trip failed: ${target3.contentToString()}" }
   println("PASS: incremental write -> bulk read")

   // --- incremental write then incremental read ---
   val reader4 = VarByteArrayFW(buffer2).setOffset(0)
   reader4.reset()
   check(reader4.read() == 1.toByte()) { "inc read[0] failed" }
   check(reader4.read() == 2.toByte()) { "inc read[1] failed" }
   check(reader4.read() == 3.toByte()) { "inc read[2] failed" }
   println("PASS: incremental write -> incremental read")

   // --- non-zero offset ---
   val buffer3 = UnsafeBuffer(ByteArray(256))
   val writer3 = VarByteArrayFW(buffer3).setOffset(50)
   writer3.encodeFrom(byteArrayOf(99, 88, 77))

   val reader5 = VarByteArrayFW(buffer3).setOffset(50)
   val target5 = ByteArray(3)
   reader5.decodeInto(target5)
   check(target5.contentEquals(byteArrayOf(99, 88, 77))) { "non-zero offset failed: ${target5.contentToString()}" }

   reader5.reset()
   check(reader5.read() == 99.toByte()) { "non-zero offset inc read[0] failed" }
   check(reader5.read() == 88.toByte()) { "non-zero offset inc read[1] failed" }
   check(reader5.read() == 77.toByte()) { "non-zero offset inc read[2] failed" }
   println("PASS: non-zero offset")

   println("ALL TESTS PASSED")
}