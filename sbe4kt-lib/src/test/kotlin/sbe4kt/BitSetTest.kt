package sbe4kt.lib

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BitSetTest {

   @Test
   fun testBitSet8() {
      var b = BitSet8()
      assertTrue(b.isEmpty())

      b = b.setBitAt(0).setBitAt(3).setBitAt(7)
      assertTrue(b[0])
      assertFalse(b[1])
      assertTrue(b[3])
      assertTrue(b[7])
      assertEquals(0b00001000.toByte(), b.getMask(3))
      assertEquals(0.toByte(), b.getMask(4))

      b = b.unSetBitAt(3)
      assertFalse(b[3])
      assertTrue(b[0])

      b = b.toggleBitAt(0)
      assertFalse(b[0])
      b = b.toggleBitAt(0)
      assertTrue(b[0])

      val s = BitSet8().set(2, true).set(5, true).set(2, false)
      assertFalse(s[2])
      assertTrue(s[5])

      assertTrue(b.clear().isEmpty())
      assertTrue(BitSet8().setBitAt(0).toString().contains("00000001"))
   }

   @Test
   fun testBitSet16() {
      var b = BitSet16()
      assertTrue(b.isEmpty())

      b = b.setBitAt(0).setBitAt(8).setBitAt(15)
      assertTrue(b[0])
      assertFalse(b[1])
      assertTrue(b[8])
      assertTrue(b[15])
      assertEquals((1 shl 8).toShort(), b.getMask(8))
      assertEquals(0.toShort(), b.getMask(5))

      b = b.unSetBitAt(8)
      assertFalse(b[8])
      assertTrue(b[15])

      b = b.toggleBitAt(15)
      assertFalse(b[15])
      b = b.toggleBitAt(15)
      assertTrue(b[15])

      val s = BitSet16().set(10, true).set(12, true).set(10, false)
      assertFalse(s[10])
      assertTrue(s[12])

      assertTrue(b.clear().isEmpty())
      assertTrue(BitSet16().setBitAt(0).toString().contains("1"))
   }

   @Test
   fun testBitSet32() {
      var b = BitSet32()
      assertTrue(b.isEmpty())

      b = b.setBitAt(0).setBitAt(16).setBitAt(31)
      assertTrue(b[0])
      assertFalse(b[1])
      assertTrue(b[16])
      assertTrue(b[31])
      assertEquals(1 shl 16, b.getMask(16))
      assertEquals(0, b.getMask(10))

      b = b.unSetBitAt(16)
      assertFalse(b[16])
      assertTrue(b[31])

      b = b.toggleBitAt(31)
      assertFalse(b[31])
      b = b.toggleBitAt(31)
      assertTrue(b[31])

      val s = BitSet32().set(20, true).set(25, true).set(20, false)
      assertFalse(s[20])
      assertTrue(s[25])

      assertTrue(b.clear().isEmpty())
      assertTrue(BitSet32().setBitAt(31).toString().contains("1"))
   }

   @Test
   fun testBitSet() {
      var b = BitSet()
      assertTrue(b.isEmpty())

      b = b.setBitAt(0).setBitAt(32).setBitAt(63)
      assertTrue(b[0])
      assertFalse(b[1])
      assertTrue(b[32])
      assertTrue(b[63])
      assertEquals(1L shl 32, b.getMask(32))
      assertEquals(0L, b.getMask(10))

      b = b.unSetBitAt(32)
      assertFalse(b[32])
      assertTrue(b[63])

      b = b.toggleBitAt(63)
      assertFalse(b[63])
      b = b.toggleBitAt(63)
      assertTrue(b[63])

      val s = BitSet().set(50, true).set(60, true).set(50, false)
      assertFalse(s[50])
      assertTrue(s[60])

      assertTrue(b.clear().isEmpty())
      assertTrue(BitSet().setBitAt(0).toString().contains("1"))
   }
}
