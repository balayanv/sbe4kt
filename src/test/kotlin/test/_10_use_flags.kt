package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE
import sbe4kt.lib.FPD

@SBE(useFlags = true)
data class OrderConfig(
   var price: FPD,
   var amount: FPD,
   var isAlgoOrder: Boolean,
   var isHidden: Boolean,
   var isGTC: Boolean,
   var isPostOnly: Boolean,
   var isReduceOnly: Boolean,
)

class UseFlagsTest {

   @Test
   fun allFlagsRoundtrip() {
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_ORDER_CONFIG))

      OrderConfig(
         price = FPD.from(100),
         amount = FPD.from(10),
         isAlgoOrder = true,
         isHidden = false,
         isGTC = true,
         isPostOnly = false,
         isReduceOnly = true
      ).encodeTo(buffer)

      val decoded = newOrderConfig()
      decoded.decodeFrom(buffer)

      assertTrue(decoded.isAlgoOrder)
      assertFalse(decoded.isHidden)
      assertTrue(decoded.isGTC)
      assertFalse(decoded.isPostOnly)
      assertTrue(decoded.isReduceOnly)
   }

   @Test
   fun flyweightFlagAccess() {
      val fw = OrderConfigRW()

      fw.price(FPD.from(50))
         .amount(FPD.from(5))
         .isAlgoOrder(true)
         .isHidden(true)
         .isGTC(false)
         .isPostOnly(true)
         .isReduceOnly(false)

      assertTrue(fw.isAlgoOrder())
      assertTrue(fw.isHidden())
      assertFalse(fw.isGTC())
      assertTrue(fw.isPostOnly())
      assertFalse(fw.isReduceOnly())
   }

   @Test
   fun printLayout() {
      randomOrderConfig().printSbeLayout()
   }
}