package example

import org.agrona.concurrent.UnsafeBuffer
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
   val source = randomMsg()
   val target = newMsg()
   val buf = UnsafeBuffer(ByteArray(MsgCodec.SIZE))
   val fw = MsgFW()
   fw.buffer.wrap(buf)
   fw.payload().decode()

   fw.encode(source)
   fw.decode(target)
   target.printSbeLayout(verbose = true)

   println(fw.toString())
}
