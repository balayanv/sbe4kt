package example

import sbe4kt.SBE


@SBE
data class Trade(
   var f1: Int,
   var f2: Int,
   var f3: Int,
)

fun main() {
   randomTrade().printSbeLayout()
}