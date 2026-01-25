package test

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE

@SBE
data class Msg1(var f1: Byte, var f2: Int, var f3: Long)

@SBE(usePadding = true)
data class Msg2(var f1: Byte, var f2: Int, var f3: Long)

class AlignmentTest {

   @Test
   fun withoutPaddingSizeIs21() {
      assertEquals(21, SBE_SIZE_MSG1)
      randomMsg1().printSbeLayout()
   }

   @Test
   fun withPaddingSizeIs32() {
      assertEquals(32, SBE_SIZE_MSG2)
      randomMsg2().printSbeLayout()
   }
}
