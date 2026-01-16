package test.type_aliases

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import sbe4kt.Message

typealias CustomerId = Long
typealias Amount = Double
typealias TransactionId = Int
typealias Timestamp = Long

@Message
data class TypeAliasMessage(
    var customerId: CustomerId,
    var amount: Amount,
    var transactionId: TransactionId,
    var timestamp: Timestamp,
    var isValid: Boolean
)

@Message
data class ComplexTypeAliasMessage(
    var primaryId: CustomerId,
    var secondaryId: CustomerId,
    var totalAmount: Amount
)

class TypeAliasesTest {
    @Test
    fun testBasicTypeAlias() {
        val original = TypeAliasMessage(
            customerId = 10001L,
            amount = 1500.75,
            transactionId = 2024001,
            timestamp = System.currentTimeMillis(),
            isValid = true
        )
        val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_TYPE_ALIAS_MESSAGE))

       val bytesWritten = original.encodeTo(buffer)
        val decoded = newTypeAliasMessage()
       val bytesRead = decoded.decodeFrom(buffer)

        assertEquals(bytesWritten, bytesRead)
        assertEquals(original, decoded)
    }

    @Test
    fun testComplexTypeAlias() {
       val original = ComplexTypeAliasMessage(
          primaryId = 5000L,
          secondaryId = 6000L,
          totalAmount = 2500.00
       )
       val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_COMPLEX_TYPE_ALIAS_MESSAGE))

       original.encodeTo(buffer)
       val decoded = newComplexTypeAliasMessage()
       decoded.decodeFrom(buffer)

       assertEquals(original, decoded)
    }

    @Test
    fun testTypeAliasBoundaries() {
       val original = ComplexTypeAliasMessage(
          primaryId = Long.MIN_VALUE,
          secondaryId = Long.MAX_VALUE,
          totalAmount = Double.MIN_VALUE
       )
       val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_COMPLEX_TYPE_ALIAS_MESSAGE))

       original.encodeTo(buffer)
       val decoded = newComplexTypeAliasMessage()
       decoded.decodeFrom(buffer)

       assertEquals(original, decoded)
    }

    @Test
    fun testRandomTypeAlias() {
        val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_TYPE_ALIAS_MESSAGE))

        repeat(50) {
           val original = randomTypeAliasMessage()
           original.encodeTo(buffer)
           val decoded = newTypeAliasMessage()
           decoded.decodeFrom(buffer)
           assertEquals(original, decoded)
        }
    }

    @Test
    fun testTypeAliasTransparency() {
        val customerId: CustomerId = 999L
        val longValue: Long = customerId
        assertEquals(999L, longValue)

        val amount: Amount = 55.55
        val doubleValue: Double = amount
        assertEquals(55.55, doubleValue, 0.001)

        val transactionId: TransactionId = 12345
        val intValue: Int = transactionId
        assertEquals(12345, intValue)
    }
}