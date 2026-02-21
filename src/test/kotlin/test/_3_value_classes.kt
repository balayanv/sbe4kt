package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE

@JvmInline
value class ValueClass1(val underlying: Int)

@SBE
data class Msg4(var f1: ValueClass1)

class ValueClassesTest {

   @Test
   fun sizeIs12() {
      assertEquals(12, Msg4Codec.SIZE)
      randomMsg4().printSbeLayout()
   }

   @Test
   fun valueClassPreserved() {
      val buffer = UnsafeBuffer(ByteArray(Msg4Codec.SIZE))

      val msg = Msg4(f1 = ValueClass1(underlying = 42))
      encodeMsg4(msg, buffer)

      val decoded = newMsg4()
      decodeMsg4(decoded, buffer)

      assertEquals(ValueClass1(42), decoded.f1)
   }
}
