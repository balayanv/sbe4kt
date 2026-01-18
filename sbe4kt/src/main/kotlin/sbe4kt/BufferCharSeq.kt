package sbe4kt

import org.agrona.DirectBuffer
import org.agrona.concurrent.UnsafeBuffer

/**
 * Zero-allocation CharSequence view over a fixed region of an UnsafeBuffer.
 * Does NOT copy data - reads directly from buffer on each access.
 *
 * This is a flyweight pattern: the same instance can be re-wrapped to point
 * to different buffer locations. The view is only valid while the underlying
 * buffer remains unchanged.
 *
 * @param maxSize The fixed size of the character sequence region in bytes
 */
class BufferCharSeq(
    private var buffer: DirectBuffer,
    private var offset: Int,
    private val maxSize: Int
) : CharSequence, Comparable<CharSequence> {

    constructor(maxSize: Int) : this(UnsafeBuffer(ByteArray(0)) as DirectBuffer, 0, maxSize)

    // Cached length (computed once per wrap, invalidated on re-wrap)
    private var cachedLength: Int = -1

    override val length: Int
        get() {
            if (cachedLength < 0) {
                cachedLength = computeLength()
            }
            return cachedLength
        }

    private fun computeLength(): Int {
        for (i in 0 until maxSize) {
            if (buffer.getByte(offset + i).toInt() == 0) return i
        }
        return maxSize
    }

    override fun get(index: Int): Char {
        if (index < 0 || index >= length) {
            throw IndexOutOfBoundsException("Index $index out of bounds for length $length")
        }
        return buffer.getByte(offset + index).toInt().toChar()
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex < 0 || endIndex > length || startIndex > endIndex) {
            throw IndexOutOfBoundsException("start=$startIndex, end=$endIndex, length=$length")
        }
        val len = endIndex - startIndex
        val chars = CharArray(len)
        for (i in 0 until len) {
            chars[i] = get(startIndex + i)
        }
        return String(chars)
    }

    /**
     * Re-wraps this view to point to a different buffer location.
     * Invalidates the cached length.
     */
    fun wrap(buffer: DirectBuffer, offset: Int): BufferCharSeq {
        this.buffer = buffer
        this.offset = offset
        this.cachedLength = -1
        return this
    }

    /**
     * Zero-allocation comparison with another CharSequence.
     */
    fun contentEquals(other: CharSequence): Boolean {
        val len = length
        if (len != other.length) return false
        for (i in 0 until len) {
            if (get(i) != other[i]) return false
        }
        return true
    }

    /**
     * Zero-allocation append to existing StringBuilder.
     */
    fun appendTo(sb: StringBuilder): StringBuilder {
        for (i in 0 until length) {
            sb.append(get(i))
        }
        return sb
    }

    /**
     * Copies content to a CharArray. Allocates a new array.
     */
    fun toCharArray(): CharArray {
        val len = length
        val chars = CharArray(len)
        for (i in 0 until len) {
            chars[i] = get(i)
        }
        return chars
    }

    override fun toString(): String {
        return String(toCharArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CharSequence) return false
        return contentEquals(other)
    }

    override fun hashCode(): Int {
        var h = 0
        for (i in 0 until length) {
            h = 31 * h + get(i).code
        }
        return h
    }

    override fun compareTo(other: CharSequence): Int {
        val len1 = length
        val len2 = other.length
        val minLen = minOf(len1, len2)
        for (i in 0 until minLen) {
            val c1 = get(i)
            val c2 = other[i]
            if (c1 != c2) return c1 - c2
        }
        return len1 - len2
    }

    companion object {
        /**
         * Zero-allocation encode: writes CharSequence directly to buffer byte-by-byte.
         * Works with String, StringBuilder, or any CharSequence implementation.
         *
         * @param buffer The target buffer
         * @param offset The offset in the buffer to write to
         * @param value The CharSequence to encode
         * @param size The fixed size (will null-pad if value is shorter)
         */
        @JvmStatic
        fun encode(buffer: UnsafeBuffer, offset: Int, value: CharSequence, size: Int) {
            val len = minOf(value.length, size)

            // Write characters as bytes (US-ASCII)
            for (i in 0 until len) {
                buffer.putByte(offset + i, value[i].code.toByte())
            }

            // Null-pad remaining bytes
            for (i in len until size) {
                buffer.putByte(offset + i, 0)
            }
        }
    }
}
