package test

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import sbe4kt.SBE
import sbe4kt.lib.FPD

@SBE(compactFlags = 8)
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
      val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_ORDER_CONFIG))

      OrderConfigW(buffer, 0)
         .price(FPD.from(50))
         .amount(FPD.from(5))
         .isAlgoOrder(true)
         .isHidden(true)
         .isGTC(false)
         .isPostOnly(true)
         .isReduceOnly(false)

      val r = OrderConfigR(buffer, 0)
      assertTrue(r.isAlgoOrder())
      assertTrue(r.isHidden())
      assertFalse(r.isGTC())
      assertTrue(r.isPostOnly())
      assertFalse(r.isReduceOnly())
   }

   @Test
   fun printLayout() {
      randomOrderConfig().printSbeLayout()
   }

   @Test
   fun sizeIsPricePlusAmountPlusOneByteFlags() {
      // 8 (header) + 8 (price) + 8 (amount) + 1 (flags uint8) = 25
      assertEquals(25, SBE_SIZE_ORDER_CONFIG)
   }
}
