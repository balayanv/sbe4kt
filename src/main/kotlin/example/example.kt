package example

import sbe4kt.META
import sbe4kt.META.Encoding.UTF8
import sbe4kt.SBE
import java.util.UUID


@SBE
data class Trade(
   var id: Long,
   var payload: Payload,

   // TODO add support for variable length string, use best practicies of sbe
   @META(encoding = UTF8)
   var str : String,
)

@SBE(isStruct = true)
data class Payload(
   var f1 : Int,
   var f2 : Int,
)

fun main() {
   val randomTrade = randomTrade(str = UUID.randomUUID().toString())
   randomTrade.printSbeLayout(flat = true)
}