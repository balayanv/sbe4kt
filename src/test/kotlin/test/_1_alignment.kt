package test.basics

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import sbe4kt.SBE

@SBE
data class BasicMessage(
    var intField: Int,
    var longField: Long,
    var booleanField: Boolean,
    var doubleField: Double,
    var floatField: Float,
    var shortField: Short,
    var byteField: Byte
)

@SBE(
   id = 500,
   usePadding = true
)
data class BasicAlignedMessage(
    var intField: Int,
    var longField: Long,
    var booleanField: Boolean,
    var doubleField: Double,
    var floatField: Float,
    var shortField: Short,
    var byteField: Byte
)

class BasicsTest {

    @Test
    fun testBasicRandomValues() {
       val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_BASIC_MESSAGE))
       repeat(100) {
           val original = randomBasicMessage()
           val bytesWritten = original.encodeTo(buffer)
           assertTrue(bytesWritten > 0)
           val decoded = newBasicMessage()
           val bytesRead = decoded.decodeFrom(buffer)
           assertEquals(bytesWritten, bytesRead)
           assertEquals(original, decoded)
        }
    }

    @Test
    fun testBasicAlignedRandomValues() {
       val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_BASIC_ALIGNED_MESSAGE))
       repeat(100) {
           val original = randomBasicAlignedMessage()
           val bytesWritten = original.encodeTo(buffer)
           assertTrue(bytesWritten > 0)
           val decoded = newBasicAlignedMessage()
           val bytesRead = decoded.decodeFrom(buffer)
           assertEquals(bytesWritten, bytesRead)
           assertEquals(original, decoded)
        }
    }

    @Test
    fun testPrintLayout() {
        val message = randomBasicMessage()
        assertDoesNotThrow { message.printSbeLayout() }
    }
}