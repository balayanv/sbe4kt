package example

import sbe4kt.SBE
import sbe4kt.lib.TimeId


@SBE
data class Msg(
   var id: TimeId,
   var payload: Payload,
)


//@SBE(isStruct = true)
//data class Payload(var x: Int, var y: Int)

sealed interface Payload {

   @SBE
   data class Failure(var timestamp: TimeId, var errorCode: Int) : Payload

   @SBE
   data class Success(var timestamp: TimeId, var value: Long) : Payload
}


fun main() {
   val fw = MsgRW()

////   val msg = Msg(TimeId(42.ns), Payload.Success(TimeId(43.ns), 7))
//   val msg = Msg(TimeId(42.ns), Payload.Failure(TimeId(44.ns), 404))
//   fw.encodeFrom(msg)
//
//   val temp = newMsg()
//   fw.decodeInto(temp)
//
//   // reading from buffer into temp
//   println(temp)
//
//   // reading directly from buffer
//   println("wrapper.id : ${fw.id()}")
//   when (fw.payloadType()) {
//      PayloadType.SUCCESS -> {
//         val success = fw.payloadSuccess()
//         println("success.time : ${success.timestamp()}")
//         println("success.value : ${success.value()}")
//      }
//      PayloadType.FAILURE -> {
//         val failure = fw.payloadFailure()
//         println("falure.time : ${failure.timestamp()}")
//         println("failure.errorCode : ${failure.errorCode()}")
//      }
//   }
//
//   msg.printSbeLayout()


   repeat(100) {
      val random = randomMsg()
      val target = newMsg()

      fw.encodeFrom(random)
      fw.decodeInto(target)

      assert(random == target)
   }

   randomMsg().printSbeLayout()
}