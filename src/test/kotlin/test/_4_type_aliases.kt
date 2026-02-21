package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE

enum class Enum2 { DEFAULT, VALUE1, VALUE2 }

typealias EnumAlias1 = Enum2

@JvmInline
value class ValueClass2(val underlying: EnumAlias1)

@JvmInline
value class ValueClass3(val underlying: ValueClass2)

@SBE
data class Msg5(var f1: ValueClass3)

class TypeAliasNestingTest {

   @Test
   fun sizeIs9() {
      assertEquals(9, Msg5Codec.SIZE)
      randomMsg5().printSbeLayout()
   }

   @Test
   fun deeplyNestedValueClassRoundtrips() {
      val buffer = UnsafeBuffer(ByteArray(Msg5Codec.SIZE))

      val msg = Msg5(f1 = ValueClass3(ValueClass2(Enum2.VALUE2)))
      encodeMsg5(msg, buffer)

      val decoded = newMsg5()
      decodeMsg5(decoded, buffer)

      assertEquals(ValueClass3(ValueClass2(Enum2.VALUE2)), decoded.f1)
   }
}
