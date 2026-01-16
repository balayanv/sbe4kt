package example

import org.agrona.concurrent.UnsafeBuffer
import sbe4kt.Message
import sbe4kt.Metadata


@Message
data class Example(
//    var f1: Boolean,
//    var f2: Char,
//    var f3: Byte,
//    var f4: Short,
    var f5: Int,
    var f6: Long,
//    var f7: Float,
//    var f8: Double,
//    var f9: EnumBool,
//    var f10: ValueInt,
    @Metadata(isComposite = true)
    var position: XY,
)

enum class EnumBool {
    FALSE, TRUE
}

@JvmInline
value class ValueInt(val value: Int)

data class XY(
    var x: Int,
//    var y: Int,
   var y: ValueInt,
)

fun main() {
    val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_EXAMPLE))

    repeat(100) {
        val original = randomExample()
        val bytesWritten = original.encodeTo(buffer)
        require(bytesWritten > 0)
        val decoded = randomExample()
        val bytesRead = decoded.decodeFrom(buffer)
        require(bytesWritten == bytesRead)
        require(original == decoded)
    }

    val wrapper = ExampleRW()

    // Test composite helper functionality
    wrapper.position().x(100)
    wrapper.position().y(ValueInt(200))
    val x = wrapper.position().x()
    val y = wrapper.position().y()
    require(x == 100) { "x should be 100 but was $x" }
    require(y == ValueInt(200)) { "y should be ValueInt(200) but was $y" }

    repeat(100) {
        val original = randomExample()
        wrapper.encode(original)

        val decoded = newExample()
        wrapper.decode(decoded)

        require(original == decoded)
    }

    randomExample().printSbeLayout()
}