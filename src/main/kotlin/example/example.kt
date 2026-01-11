package example

import org.agrona.concurrent.UnsafeBuffer
import sbe4kt.SBE

@SBE
data class SomeMessage(
   var f1: Boolean,
   var f2: Char,
   var f3: Byte,
   var f4: Short,
   var f5: Int,
   var f6: Long,
   var f7: Float,
   var f8: Double,
)

fun main() {
   val message = randomSomeMessage()
   val buffer = UnsafeBuffer(ByteArray(1024))
   val bytesWritten = encodeSomeMessage(buffer, message)

   println("$message -> byte[$bytesWritten]")

   val newMessage = newSomeMessage()
   val bytesRead = decodeSomeMessage(buffer, newMessage)

   println("byte[$bytesRead] -> $newMessage")

   println("\nRandom message:")
   val randomMsg = randomSomeMessage()
   println(randomMsg)
   randomMsg.printSbeLayout()
}