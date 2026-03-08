package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE
import sbe4kt.META

@SBE
data class AllArraysMsg(
   @META(size = 8) var bytes: ByteArray,
   @META(size = 6) var shorts: ShortArray,
   @META(size = 5) var ints: IntArray,
   @META(size = 4) var longs: LongArray,
   @META(size = 5) var floats: FloatArray,
   @META(size = 4) var doubles: DoubleArray,
   @META(size = 10) var chars: CharArray,
)

class PrimitiveArrayTest {

   @Test
   fun size() {
      // 8 header + 8 bytes + 12 shorts (6*2) + 20 ints (5*4) + 32 longs (4*8) + 20 floats (5*4) + 32 doubles (4*8) + 10 chars (10*1) = 142
      assertEquals(142, AllArraysMsgCodec.SIZE)
   }

   @Test
   fun codecRoundTrip() {
      val buffer = UnsafeBuffer(ByteArray(AllArraysMsgCodec.SIZE))
      val original = AllArraysMsg(
         bytes = ByteArray(8) { it.toByte() },
         shorts = ShortArray(6) { (it * 100).toShort() },
         ints = IntArray(5) { it * 1000 },
         longs = LongArray(4) { it * 10000L },
         floats = FloatArray(5) { it * 1.5f },
         doubles = DoubleArray(4) { it * 2.5 },
         chars = "ABCDEFGHIJ".toCharArray(),
      )

      AllArraysMsgCodec.encodeFrom(original, buffer)
      val decoded = newAllArraysMsg()
      AllArraysMsgCodec.decodeInto(decoded, buffer)

      assertArrayEquals(original.bytes, decoded.bytes)
      assertArrayEquals(original.shorts, decoded.shorts)
      assertArrayEquals(original.ints, decoded.ints)
      assertArrayEquals(original.longs, decoded.longs)
      assertArrayEquals(original.floats, decoded.floats, 0.0f)
      assertArrayEquals(original.doubles, decoded.doubles, 0.0)
      assertArrayEquals(original.chars, decoded.chars)
   }

   @Test
   fun flyweightElementAccess() {
      val fw = AllArraysMsgFW().init().header()

      for (i in 0 until 8) fw.bytes().set(i, (i * 3).toByte())
      for (i in 0 until 6) fw.shorts().set(i, (i * 7).toShort())
      for (i in 0 until 5) fw.ints().set(i, i * 11)
      for (i in 0 until 4) fw.longs().set(i, i * 13L)
      for (i in 0 until 5) fw.floats().set(i, i * 0.5f)
      for (i in 0 until 4) fw.doubles().set(i, i * 0.25)
      for (i in 0 until 10) fw.chars().set(i, ('a' + i))

      for (i in 0 until 8) assertEquals((i * 3).toByte(), fw.bytes().get(i))
      for (i in 0 until 6) assertEquals((i * 7).toShort(), fw.shorts().get(i))
      for (i in 0 until 5) assertEquals(i * 11, fw.ints().get(i))
      for (i in 0 until 4) assertEquals(i * 13L, fw.longs().get(i))
      for (i in 0 until 5) assertEquals(i * 0.5f, fw.floats().get(i))
      for (i in 0 until 4) assertEquals(i * 0.25, fw.doubles().get(i))
      for (i in 0 until 10) assertEquals('a' + i, fw.chars().get(i))
   }

   @Test
   fun flyweightBulkAccess() {
      val fw = AllArraysMsgFW().init().header()

      val bytes = ByteArray(8) { (it + 10).toByte() }
      val shorts = ShortArray(6) { (it + 20).toShort() }
      val ints = IntArray(5) { it + 30 }
      val longs = LongArray(4) { it + 40L }
      val floats = FloatArray(5) { it + 50f }
      val doubles = DoubleArray(4) { it + 60.0 }
      val chars = "0123456789".toCharArray()

      fw.bytes().encodeFrom(bytes)
      fw.shorts().encodeFrom(shorts)
      fw.ints().encodeFrom(ints)
      fw.longs().encodeFrom(longs)
      fw.floats().encodeFrom(floats)
      fw.doubles().encodeFrom(doubles)
      fw.chars().encodeFrom(chars)

      val dBytes = ByteArray(8); fw.bytes().decodeInto(dBytes)
      val dShorts = ShortArray(6); fw.shorts().decodeInto(dShorts)
      val dInts = IntArray(5); fw.ints().decodeInto(dInts)
      val dLongs = LongArray(4); fw.longs().decodeInto(dLongs)
      val dFloats = FloatArray(5); fw.floats().decodeInto(dFloats)
      val dDoubles = DoubleArray(4); fw.doubles().decodeInto(dDoubles)
      val dChars = CharArray(10); fw.chars().decodeInto(dChars)

      assertArrayEquals(bytes, dBytes)
      assertArrayEquals(shorts, dShorts)
      assertArrayEquals(ints, dInts)
      assertArrayEquals(longs, dLongs)
      assertArrayEquals(floats, dFloats, 0.0f)
      assertArrayEquals(doubles, dDoubles, 0.0)
      assertArrayEquals(chars, dChars)
   }

   @Test
   fun flyweightRoundTrip() {
      val fw = AllArraysMsgFW().init()
      val original = AllArraysMsg(
         bytes = ByteArray(8) { (it * 5).toByte() },
         shorts = ShortArray(6) { (it * 50).toShort() },
         ints = IntArray(5) { it * 500 },
         longs = LongArray(4) { it * 5000L },
         floats = FloatArray(5) { it * 3.14f },
         doubles = DoubleArray(4) { it * 2.718 },
         chars = "HelloWorld".toCharArray(),
      )

      fw.encodeFrom(original)
      val decoded = newAllArraysMsg()
      fw.decodeInto(decoded)

      assertArrayEquals(original.bytes, decoded.bytes)
      assertArrayEquals(original.shorts, decoded.shorts)
      assertArrayEquals(original.ints, decoded.ints)
      assertArrayEquals(original.longs, decoded.longs)
      assertArrayEquals(original.floats, decoded.floats, 0.0f)
      assertArrayEquals(original.doubles, decoded.doubles, 0.0)
      assertArrayEquals(original.chars, decoded.chars)
   }

   @Test
   fun charArrayFWIsCharSequence() {
      val fw = AllArraysMsgFW().init().header()
      fw.chars().encodeFromCharSequence("HelloWorld")
      assertEquals("HelloWorld", fw.chars().toString())
      assertEquals(10, fw.chars().length)
      assertEquals('e', fw.chars().get(1))
   }

   @Test
   fun charArrayFWPartialEncode() {
      val fw = AllArraysMsgFW().init().header()
      fw.chars().encodeFromCharSequence("hello world", 6, 11)
      assertEquals("world", fw.chars().toString())
   }

   @Test
   fun sbeRoundTrip() {
      val original = AllArraysMsg(
         bytes = ByteArray(8) { (it * 2).toByte() },
         shorts = ShortArray(6) { (it * 200).toShort() },
         ints = IntArray(5) { it * 2000 },
         longs = LongArray(4) { it * 20000L },
         floats = FloatArray(5) { it * 1.1f },
         doubles = DoubleArray(4) { it * 9.9 },
         chars = "SBE4KOTLIN".toCharArray(),
      )
      val buffer = UnsafeBuffer(ByteArray(256))
      encodeAllArraysMsgSBE(original, buffer)
      val decoded = newAllArraysMsg()
      decodeAllArraysMsgSBE(decoded, buffer)

      assertArrayEquals(original.bytes, decoded.bytes)
      assertArrayEquals(original.shorts, decoded.shorts)
      assertArrayEquals(original.ints, decoded.ints)
      assertArrayEquals(original.longs, decoded.longs)
      assertArrayEquals(original.floats, decoded.floats, 0.0f)
      assertArrayEquals(original.doubles, decoded.doubles, 0.0)
      assertArrayEquals(original.chars, decoded.chars)
   }
}
