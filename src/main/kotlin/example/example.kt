package example

import org.agrona.collections.IntArrayList
import sbe4kt.SBE


@SBE
data class Trade(
   var id: Long,
   var list : IntArrayList,
)

@SBE(isStruct = true)
//@SBE
data class Payload(
   var f1 : Int,
   var f2 : Int,
)

enum class En{
   Val1, Val2
}

fun main() {
   val source = randomTrade()

   println(source)
   val buffer = org.agrona.ExpandableArrayBuffer()
   val bytesWritten = source.encodeTo(buffer)
   println("bytesWritten : $bytesWritten")

   val target = newTrade()
   val bytesRead = target.decodeFrom(buffer)
   println("bytesRead : $bytesRead")

   println(target)
   target.printSbeLayout(flat = true)
}