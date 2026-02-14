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
      assertEquals(12, SBE_SIZE_MSG4)
      randomMsg4().printSbeLayout()
   }

   @Test
   fun valueClassPreserved() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_MSG4))

      val msg = Msg4(f1 = ValueClass1(underlying = 42))
      wrapAndEncodeMsg4(msg, buffer)

      val decoded = newMsg4()
      wrapAndDecodeMsg4(decoded, buffer)

      assertEquals(ValueClass1(42), decoded.f1)
   }
}
