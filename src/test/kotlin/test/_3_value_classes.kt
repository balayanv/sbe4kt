package test.value_classes

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sbe4kt.SBE

@JvmInline
value class ValueBoolean(val value : Boolean)
@JvmInline
value class ValueChar(val value : Char)
@JvmInline
value class ValueByte(val value : Byte)
@JvmInline
value class ValueShort(val value : Short)
@JvmInline
value class ValueInt(val value : Int)
@JvmInline
value class ValueFloat(val value : Float)
@JvmInline
value class ValueDouble(val value : Double)

enum class SomeEnum(val value: String) {
   UNDEFINED("something"),
   SOMEVALUE("somewhere")
}

@JvmInline
value class ValueEnum(val value : SomeEnum)

@SBE
data class ValueClassMessage(
   var f1: ValueBoolean,
   var f2: ValueChar,
   var f3: ValueByte,
   var f4: ValueShort,
   var f5: ValueInt,
   var f6: ValueLong,
   var f7: ValueFloat,
   var f8: ValueDouble,
   var f9: ValueEnum,
){
   @JvmInline
   value class ValueLong(val value : Long)

}


class ValueClassesTest {
   @Test
   fun testValueClassSerialization() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_VALUE_CLASS_MESSAGE))
      repeat(100) {
         val original = randomValueClassMessage()
         val bytesWritten = original.encodeTo(buffer)
         assertTrue(bytesWritten > 0)
         val decoded = newValueClassMessage()
         val bytesRead = decoded.decodeFrom(buffer)
         assertEquals(bytesWritten, bytesRead)
         assertEquals(original, decoded)
      }
   }
}