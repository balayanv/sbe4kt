//package example
//
//import sbe4kt.META
//import sbe4kt.SBE
//import sbe4kt.lib.TimeId
//
//@SBE
//data class Wrapper(
//   var id: TimeId,
//   @META(oneOf = [Failure::class, Success::class])
//   var result: Any,
//)
//
//@SBE(id = 2)
//data class Failure(var id: TimeId, var code: Int)
//
//@SBE(id = 1)
//data class Success(var id: TimeId, var result: Long)
//
//fun main() {
//   val msg = Wrapper(
//      id = TimeId.now(),
//      result = newSuccess(TimeId.now(), 42L)
//   )
//
//   // unlike java kotlin has cutting edge features such as
//   // not needing to cast to a type if you just checked its type...
//   when (val payload = msg.result) {
//      is Success -> println("success : ${payload.id} ${payload.result}") // hence payload is of type Success here
//      is Failure -> println("failure : ${payload.id} ${payload.code}") // same goes for failure case
//   }
//
//   msg.printSbeLayout()
//   randomSuccess().printSbeLayout()
//}