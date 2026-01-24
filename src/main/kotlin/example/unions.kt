package example

import sbe4kt.META
import sbe4kt.SBE
import sbe4kt.lib.TimeId

@SBE
data class Wrapper(
   var id: TimeId,
   @META(oneOf = [Failure::class, Success::class])
   var result: Any,
)

@SBE(id = 2)
data class Failure(var id: TimeId, var code: Int)

@SBE(id = 1)
data class Success(var id: TimeId, var result: Long)

fun main() {
   val msg = Wrapper(
      id = TimeId.now(),
      result = newSuccess(TimeId.now(), 42L)
   )

   msg.printSbeLayout()
}