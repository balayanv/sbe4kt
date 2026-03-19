package example

import sbe4kt.SBE
import java.util.UUID


@SBE
data class Trade(
   var id: Long,
//   var payloads: Array<Int>,
//   @META(encoding = UTF8)
   var list : List<String>,
//   var payload: Payload,
)

@SBE(isStruct = true)
//@SBE
data class Payload(
   var f1 : Int,
   var f2 : Int,
)

fun main() {
   val source = randomTrade()

   val buffer = org.agrona.ExpandableArrayBuffer()
   val bytesWritten = source.encodeTo(buffer)
   println("bytesWritten : $bytesWritten")

   val target = newTrade()
   val bytesRead = target.decodeFrom(buffer)
   println("bytesRead : $bytesRead")

   target.printSbeLayout(flat = true)
}