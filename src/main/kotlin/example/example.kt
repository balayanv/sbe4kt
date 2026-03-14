package example

import sbe4kt.META
import sbe4kt.SBE


@SBE
data class Trade(
   var id: Long,
   var payload: Payload,

   @META(size = 12)
   var str : String,
)

@SBE(isStruct = true)
data class Payload(
   var f1 : Int,
   var f2 : Int,
)

fun main() {
   val randomTrade = randomTrade()
   randomTrade.printSbeLayout(flat = true)
}