package test.charsequence

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sbe4kt.BufferCharSeq
import sbe4kt.META
import sbe4kt.SBE

@SBE
data class OrderMessage(
    var orderId: Long,
    @META(size = 3)
    var currency: String,
    @META(size = 8)
    var symbol: String,
    var quantity: Int,
    var price: Double
)

@SBE
data class CharSeqOnlyMessage(
    @META(size = 4)
    var code: String,
    @META(size = 16)
    var description: String
)

class FixedCharSeqTest {

    @Test
    fun testBufferCharSeqBasics() {
        val buffer = UnsafeBuffer(ByteArray(16))
        // Write "HELLO" to buffer
        buffer.putByte(0, 'H'.code.toByte())
        buffer.putByte(1, 'E'.code.toByte())
        buffer.putByte(2, 'L'.code.toByte())
        buffer.putByte(3, 'L'.code.toByte())
        buffer.putByte(4, 'O'.code.toByte())
        buffer.putByte(5, 0) // null terminator

        val view = BufferCharSeq(buffer, 0, 8)

        assertEquals(5, view.length)
        assertEquals('H', view[0])
        assertEquals('E', view[1])
        assertEquals('L', view[2])
        assertEquals('L', view[3])
        assertEquals('O', view[4])
        assertEquals("HELLO", view.toString())
    }

    @Test
    fun testBufferCharSeqFullCapacity() {
        val buffer = UnsafeBuffer(ByteArray(8))
        // Write "ABCDEFGH" - fills entire capacity, no null terminator needed
        for (i in 0 until 8) {
            buffer.putByte(i, ('A'.code + i).toByte())
        }

        val view = BufferCharSeq(buffer, 0, 8)

        assertEquals(8, view.length)
        assertEquals("ABCDEFGH", view.toString())
    }

    @Test
    fun testBufferCharSeqEncode() {
        val buffer = UnsafeBuffer(ByteArray(16))

        // Encode a string
        BufferCharSeq.encode(buffer, 0, "TEST", 8)

        // Verify bytes
        assertEquals('T'.code.toByte(), buffer.getByte(0))
        assertEquals('E'.code.toByte(), buffer.getByte(1))
        assertEquals('S'.code.toByte(), buffer.getByte(2))
        assertEquals('T'.code.toByte(), buffer.getByte(3))
        assertEquals(0.toByte(), buffer.getByte(4)) // null padding
        assertEquals(0.toByte(), buffer.getByte(5))
        assertEquals(0.toByte(), buffer.getByte(6))
        assertEquals(0.toByte(), buffer.getByte(7))

        // Read back via view
        val view = BufferCharSeq(buffer, 0, 8)
        assertEquals("TEST", view.toString())
    }

    @Test
    fun testBufferCharSeqEncodeWithStringBuilder() {
        val buffer = UnsafeBuffer(ByteArray(16))

        // Encode from StringBuilder (no String allocation)
        val sb = StringBuilder("DYNAMIC")
        BufferCharSeq.encode(buffer, 0, sb, 8)

        val view = BufferCharSeq(buffer, 0, 8)
        assertEquals("DYNAMIC", view.toString())
    }

    @Test
    fun testBufferCharSeqEncodeTruncation() {
        val buffer = UnsafeBuffer(ByteArray(8))

        // Encode a string longer than capacity - should truncate
        BufferCharSeq.encode(buffer, 0, "VERYLONGSTRING", 8)

        val view = BufferCharSeq(buffer, 0, 8)
        assertEquals(8, view.length)
        assertEquals("VERYLONG", view.toString())
    }

    @Test
    fun testBufferCharSeqContentEquals() {
        val buffer = UnsafeBuffer(ByteArray(16))
        BufferCharSeq.encode(buffer, 0, "HELLO", 8)

        val view = BufferCharSeq(buffer, 0, 8)

        assertTrue(view.contentEquals("HELLO"))
        assertTrue(view.contentEquals(StringBuilder("HELLO")))
        assertFalse(view.contentEquals("WORLD"))
        assertFalse(view.contentEquals("HELL"))
    }

    @Test
    fun testBufferCharSeqComparison() {
        val buffer = UnsafeBuffer(ByteArray(32))
        BufferCharSeq.encode(buffer, 0, "APPLE", 8)
        BufferCharSeq.encode(buffer, 8, "BANANA", 8)
        BufferCharSeq.encode(buffer, 16, "APPLE", 8)

        val view1 = BufferCharSeq(buffer, 0, 8)
        val view2 = BufferCharSeq(buffer, 8, 8)
        val view3 = BufferCharSeq(buffer, 16, 8)

        assertTrue(view1 < view2) // APPLE < BANANA
        assertEquals(0, view1.compareTo(view3)) // APPLE == APPLE
        assertTrue(view2 > view1) // BANANA > APPLE
    }

    @Test
    fun testBufferCharSeqRewrap() {
        val buffer = UnsafeBuffer(ByteArray(32))
        BufferCharSeq.encode(buffer, 0, "FIRST", 8)
        BufferCharSeq.encode(buffer, 8, "SECOND", 8)

        val view = BufferCharSeq(buffer, 0, 8)
        assertEquals("FIRST", view.toString())

        // Re-wrap to different offset
        view.wrap(buffer, 8)
        assertEquals("SECOND", view.toString())
    }

    @Test
    fun testBufferCharSeqAppendTo() {
        val buffer = UnsafeBuffer(ByteArray(16))
        BufferCharSeq.encode(buffer, 0, "HELLO", 8)

        val view = BufferCharSeq(buffer, 0, 8)
        val sb = StringBuilder("PREFIX_")

        view.appendTo(sb)
        assertEquals("PREFIX_HELLO", sb.toString())
    }

    @Test
    fun testBufferCharSeqSubSequence() {
        val buffer = UnsafeBuffer(ByteArray(16))
        BufferCharSeq.encode(buffer, 0, "HELLO", 8)

        val view = BufferCharSeq(buffer, 0, 8)
        val sub = view.subSequence(1, 4)

        assertEquals("ELL", sub.toString())
    }

    @Test
    fun testOrderMessageEncodeDecode() {
        val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_ORDER_MESSAGE))

        val original = newOrderMessage(
            orderId = 12345L,
            currency = "USD",
            symbol = "BTCUSD",
            quantity = 100,
            price = 50000.50
        )

        encodeOrderMessage(original, buffer)

        val decoded = newOrderMessage()
        decodeOrderMessage(decoded, buffer)

        assertEquals(original.orderId, decoded.orderId)
        assertEquals(original.currency, decoded.currency)
        assertEquals(original.symbol, decoded.symbol)
        assertEquals(original.quantity, decoded.quantity)
        assertEquals(original.price, decoded.price)
    }

    @Test
    fun testOrderMessageWithStringBuilder() {
        val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_ORDER_MESSAGE))

        // Create message with StringBuilder values
        val original = OrderMessage(
            orderId = 99999L,
            currency = StringBuilder("EUR").toString(),
            symbol = StringBuilder("ETHUSD").toString(),
            quantity = 50,
            price = 3000.25
        )

        encodeOrderMessage(original, buffer)

        val decoded = newOrderMessage()
        decodeOrderMessage(decoded, buffer)

        assertEquals("EUR", decoded.currency)
        assertEquals("ETHUSD", decoded.symbol)
    }

    @Test
    fun testOrderMessageFlyweightRW() {
        val flyweight = OrderMessageRW()

        flyweight.orderId(12345L)
        flyweight.currency("GBP")
        flyweight.symbol("XRPUSD")
        flyweight.quantity(200)
        flyweight.price(1.50)

        assertEquals(12345L, flyweight.orderId())
        assertEquals("GBP", flyweight.currency().toString())
        assertEquals("XRPUSD", flyweight.symbol().toString())
        assertEquals(200, flyweight.quantity())
        assertEquals(1.50, flyweight.price())

        // Decode into object
        val decoded = flyweight.decode()
        assertEquals(12345L, decoded.orderId)
        assertEquals("GBP", decoded.currency)
        assertEquals("XRPUSD", decoded.symbol)
    }

    @Test
    fun testOrderMessageFlyweightCharSeqInterface() {
        val flyweight = OrderMessageRW()
        flyweight.symbol("BTCUSD")

        // Get as CharSequence (zero allocation)
        val symbolSeq: CharSequence = flyweight.symbol()

        // Can use CharSequence methods
        assertEquals(6, symbolSeq.length)
        assertEquals('B', symbolSeq[0])
        assertEquals('T', symbolSeq[1])
        assertTrue(symbolSeq.toString().startsWith("BTC"))
    }

    @Test
    fun testOrderMessageFlyweightWithStringBuilder() {
        val flyweight = OrderMessageRW()

        // Write using StringBuilder (no String allocation on encode)
        val sb = StringBuilder("DOGEUSD")
        flyweight.symbol(sb)

        // Verify
        assertEquals("DOGEUSD", flyweight.symbol().toString())
    }

    @Test
    fun testCharSeqOnlyMessage() {
        val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_CHAR_SEQ_ONLY_MESSAGE))

        val original = newCharSeqOnlyMessage(
            code = "ABCD",
            description = "Test Description"
        )

        encodeCharSeqOnlyMessage(original, buffer)

        val decoded = newCharSeqOnlyMessage()
        decodeCharSeqOnlyMessage(decoded, buffer)

        assertEquals("ABCD", decoded.code)
        assertEquals("Test Description", decoded.description)
    }

    @Test
    fun testCharSeqTooLongThrows() {
        val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_ORDER_MESSAGE))

        // SBE throws IndexOutOfBoundsException if string is too long (fail-fast behavior)
        val original = newOrderMessage(
            orderId = 1L,
            currency = "USDT", // 4 chars, exceeds 3-byte limit
            symbol = "BTCUSD",
            quantity = 1,
            price = 1.0
        )

        assertThrows<IndexOutOfBoundsException> {
            encodeOrderMessage(original, buffer)
        }
    }

    @Test
    fun testCharSeqExactSize() {
        val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_ORDER_MESSAGE))

        // Exact size should work fine
        val original = newOrderMessage(
            orderId = 1L,
            currency = "USD", // Exactly 3 bytes
            symbol = "BTCUSDAB", // Exactly 8 bytes
            quantity = 1,
            price = 1.0
        )

        encodeOrderMessage(original, buffer)

        val decoded = newOrderMessage()
        decodeOrderMessage(decoded, buffer)

        assertEquals("USD", decoded.currency)
        assertEquals("BTCUSDAB", decoded.symbol)
    }

    @Test
    fun testRandomOrderMessage() {
        // Verify randomOrderMessage generates valid data
        val random = randomOrderMessage()

        assertNotNull(random.currency)
        assertNotNull(random.symbol)
        assertTrue(random.currency.length <= 3)
        assertTrue(random.symbol.length <= 8)
    }

    @Test
    fun testMultipleMessagesInBuffer() {
        val bufferSize = SBE_SIZE_ORDER_MESSAGE * 3
        val buffer = UnsafeBuffer(ByteArray(bufferSize))

        val msg1: OrderMessage = newOrderMessage(orderId = 1, currency = "USD", symbol = "BTCUSD", quantity = 10, price = 100.0)
        val msg2 = newOrderMessage(orderId = 2, currency = "EUR", symbol = "ETHUSD", quantity = 20, price = 200.0)
        val msg3 = newOrderMessage(orderId = 3, currency = "GBP", symbol = "XRPUSD", quantity = 30, price = 300.0)

        encodeOrderMessage(msg1, buffer, 0)
        encodeOrderMessage(msg2, buffer, SBE_SIZE_ORDER_MESSAGE)
        encodeOrderMessage(msg3, buffer, SBE_SIZE_ORDER_MESSAGE * 2)

        // Decode with flyweights at different offsets
        val fw1 = OrderMessageRW().wrap(buffer, 0)
        val fw2 = OrderMessageRW().wrap(buffer, SBE_SIZE_ORDER_MESSAGE)
        val fw3 = OrderMessageRW().wrap(buffer, SBE_SIZE_ORDER_MESSAGE * 2)

        assertEquals("USD", fw1.currency().toString())
        assertEquals("EUR", fw2.currency().toString())
        assertEquals("GBP", fw3.currency().toString())

        assertEquals("BTCUSD", fw1.symbol().toString())
        assertEquals("ETHUSD", fw2.symbol().toString())
        assertEquals("XRPUSD", fw3.symbol().toString())
    }

    @Test
    fun testWriteOnlyFlyweight() {
        val msgW = OrderMessageW()

        msgW.orderId(555L)
            .currency("JPY")
            .symbol("SOLUSD")
            .quantity(99)
            .price(25.0)

        // Verify by decoding from the same buffer
        val decoded = newOrderMessage()
        decodeOrderMessage(decoded, msgW.buffer)

        assertEquals(555L, decoded.orderId)
        assertEquals("JPY", decoded.currency)
        assertEquals("SOLUSD", decoded.symbol)
    }

    @Test
    fun testReadOnlyFlyweight() {
        val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_ORDER_MESSAGE))
        val original = newOrderMessage(
            orderId = 777L,
            currency = "CHF",
            symbol = "ADAUSD",
            quantity = 42,
            price = 0.45
        )
        encodeOrderMessage(original, buffer)

        val msgR = OrderMessageR().wrap(buffer, 0)

        assertEquals(777L, msgR.orderId())
        assertEquals("CHF", msgR.currency().toString())
        assertEquals("ADAUSD", msgR.symbol().toString())
        assertEquals(42, msgR.quantity())
        assertEquals(0.45, msgR.price())
    }
}
