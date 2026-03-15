package example

import sbe4kt.META
import sbe4kt.META.Encoding.UTF8
import sbe4kt.SBE
import java.util.UUID


@SBE
data class Trade(
   var id: Long,
   var payloads: Array<Payload>,
   @META(encoding = UTF8)
   var str : String,
)

@SBE(isStruct = true)
data class Payload(
   var f1 : Int,
   var f2 : Int,
)

fun main() {
   val original = randomTrade(str = UUID.randomUUID().toString())
   original.printSbeLayout(flat = true)

   // round-trip test
   val buffer = org.agrona.ExpandableArrayBuffer()
   original.encodeTo(buffer)
   val decoded = newTrade()
   decoded.decodeFrom(buffer)
   check(original.id == decoded.id) { "id mismatch" }
   check(original.payloads.contentEquals(decoded.payloads)) { "payloads mismatch" }
   check(original.str == decoded.str) { "str mismatch" }
   println("Round-trip OK: $decoded")
}