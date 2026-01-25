package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE

enum class Enum1 { DEFAULT, VALUE1, VALUE2 }

@SBE
data class Msg3(var f1: Enum1)

class EnumsTest {

   @Test
   fun eachEnumValueRoundtrips() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_MSG3))

      for (value in Enum1.entries) {
         Msg3(f1 = value).encodeTo(buffer)

         val decoded = newMsg3()
         decoded.decodeFrom(buffer)

         assertEquals(value, decoded.f1)
      }
   }
}
