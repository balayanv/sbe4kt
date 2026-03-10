package example

import org.agrona.concurrent.UnsafeBuffer
import sbe4kt.SBE
import sbe4kt.lib.TimeId


@SBE
data class Msg(
   var id: TimeId,
   var payload: Payload,
)


///**
// * Variable length byte[] reader.
// */
//class PayloadArrayFW(val buffer: UnsafeBuffer){
//   private var offset: Int = 0
//   private var dataOffset: Int = offset + LENGTH_SIZE
//   private var readPos = offset + 1
//   private var writePos = readPos
//
//   fun offset(offset: Int) = apply {
//      this.offset = offset
//      this.dataOffset = offset + LENGTH_SIZE
//      reset()
//   }
//
//   /**
//    * Must be called before incremental reads or writes.
//    * Can be skipped if setOffset() was called right before.
//    */
//   fun reset() {
//      val pos = offset + LENGTH_SIZE - 1
//      readPos = pos
//      writePos = pos
//   }
//
//   /**
//    * Size in bytes.
//    */
//   fun size(): Int = LENGTH_SIZE + ByteArrayCodec.size(length())
//
//   /**
//    * Record unsigned 2 byte size at offset.
//    */
//   fun size(size: Int) {
//      buffer.putShort(offset, size.toUShort().toShort())
//   }
//
//   /**
//    * Number of elements in this Array.
//    */
//   fun length(): Int = buffer.getShort(offset).toInt()
//
//   fun read(): Byte = buffer.getByte(++readPos)
//   fun write(value: Byte) = apply { buffer.putByte(++writePos, value) }
//
//   fun endWrite(): Int {
//      val size = writePos - offset - 1
//      buffer.putShort(offset, size.toShort())
//      return size
//   }
//
//   fun decodeInto(target: ByteArray): Int {
//      val size = length()
//      val length = minOf(size, target.size)
//      ByteArrayCodec.decodeInto(target, 0, length, buffer, dataOffset)
//      return length
//   }
//
//   fun decodeInto(target: ByteArray, from: Int, to: Int): Int {
//      val size = length()
//      val length = minOf(to - from, size)
//      ByteArrayCodec.decodeInto(target, from, length, buffer, dataOffset)
//      return size
//   }
//
//   fun encodeFrom(source: ByteArray): Int {
//      val length = minOf(source.size, LENGTH_MAX_VALUE)
//      buffer.putShort(offset, length.toShort())
//      ByteArrayCodec.encodeFrom(source, 0, length, buffer, dataOffset)
//      return length
//   }
//
//   fun encodeFrom(source: ByteArray, from: Int = 0, to: Int = source.size): Int {
//      val length = minOf(to - from, LENGTH_MAX_VALUE)
//      buffer.putShort(offset, length.toShort())
//      ByteArrayCodec.encodeFrom(source, from, length, buffer, dataOffset)
//      return length
//   }
//}

@SBE(isStruct = true)
data class Payload(var x: Int, var y: Int)

fun main() {
   val source = randomMsg()
   val target = newMsg()
   val buf = UnsafeBuffer(ByteArray(MsgCodec.SIZE))
   val fw = MsgFW()
   fw.buffer.wrap(buf)
   fw.payload()

   fw.encodeFrom(source)
   fw.decodeInto(target)
   target.printSbeLayout(verbose = true)

   println(fw.toString())
}
