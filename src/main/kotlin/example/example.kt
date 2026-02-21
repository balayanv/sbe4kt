package example

import sbe4kt.SBE
import sbe4kt.lib.TimeId


@SBE
data class Msg(
   var id: TimeId,
   var payload: Payload,
)

@SBE(isStruct = true)
data class Payload(var x: Int, var y: Int)


fun main() {
   val fw = MsgRW()

   repeat(100) {
      val random = randomMsg()
      val target = randomMsg()

      fw.encodeFrom(random)
      fw.decodeInto(target)

      assert(random == target)
   }
   randomMsg().printSbeLayout()
}
