package example

import sbe4kt.META
import sbe4kt.SBE
import sbe4kt.lib.TimeId
import java.util.Objects


@SBE
data class Msg(
   var id: TimeId,
//   var payload: Payload,
   @META(size = 8)
   var arr : IntArray,
)

@SBE(isStruct = true)
data class Payload(var x: Int, var y: Int)

fun main() {

//   ByteArray
//   ShortArray
//   IntArray
//   LongArray
//   DoubleArray
//   FloatArray

   val source = randomMsg()
   val target = newMsg()

   val fw = MsgFW()
   fw.encodeFrom(source)



//   fw.data()[0]
//      .set(0, 'A').set(1, 'B').set(2, 'C').set(3, 0)
//      .encodeFromCharSequence("ABC")

   fw.decodeInto(target)
   println(source)
   println(target)
   target.printSbeLayout(verbose = true)

   println(fw.toString())
}
