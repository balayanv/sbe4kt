package example

import org.agrona.ExpandableArrayBuffer
import sbe4kt.SBE


@SBE(allowNullableTypes = true, compactFlags = 8)
data class Trade(
   var f1: Int,
   var f2: Long,
   var f3: Double,
   var isThisEnabled: Boolean,
   var isThatEnabled: Boolean,
   var isSkyBlue: Boolean,
   var payload: Payload,
   var str1 : StringBuilder?,
//   var str2 : StringBuilder,
//   var str3 : StringBuilder,
)

@SBE(isStruct = true)
data class Payload(
   var f1: Int,
   var f2: Int,
)

fun main() {
   val source = randomTrade()

   val buffer = ExpandableArrayBuffer()
   val writer = TradeW(buffer)
   writer.encodeFrom(source)

   val target = newTrade()
   val reader = TradeR(buffer)
   reader.decodeInto(target)

   target.printSbeLayout()
//   target.printSbeLayout(flat = true, verbose = true)
}