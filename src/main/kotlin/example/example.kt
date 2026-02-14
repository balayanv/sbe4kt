package example

import sbe4kt.META
import sbe4kt.SBE


@SBE
data class Trade(
   var id: Long,
   var price: Long,
   var amount: Long,
   var flags: Long,
)

//@SBE
//data class Quote(
//   var bid: Long,
//   var ask: Long,
//)

fun main() {
   randomTrade().printSbeLayout()
}