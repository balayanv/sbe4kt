package example

import sbe4kt.SBE
import java.util.UUID


@SBE
data class Trade(
   var id: Long,
//   var payloads: Array<Payload>,
//   @META(encoding = UTF8)
   var str : String,
)

@SBE(isStruct = true)
data class Payload(
   var f1 : Int,
   var f2 : Int,
)

fun main() {
   val rw = TradeRW()
   val source = randomTrade(str = UUID.randomUUID().toString())
   rw.encodeFrom(source)

   val target = newTrade()
   rw.decodeInto(target)

   target.printSbeLayout()

}