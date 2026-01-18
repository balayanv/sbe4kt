package sbe4kt.lib

@JvmInline
value class TimeId(val value: Long) {

    /** Returns the time in whole milliseconds */
    fun millis(): Long = value / 1_000_000

    /** Returns the time in whole microseconds */
    fun micros(): Long = value / 1_000

    /** Returns the raw nanosecond timestamp (including the tag) */
    fun nanos(): Long = value

    /** * Extracts the ID/Tag from the last 2 digits.
     * Range: 0 to 99
     */
    fun tag(): Int = (value % 100).toInt() // Least significant 2 digits

    /** * Returns a COPY of this time with the tag updated.
     * @param newTag Must be between 0 and 99
     */
    fun tag(newTag: Int): TimeId {
        require(newTag in 0..99) { "Tag must be between 0 and 99" }
        val base = (value / 100) * 100
        return TimeId(base + newTag)
    }

    fun nanos(newValue: Long): TimeId = TimeId(newValue).tag(tag())

    override fun toString(): String = value.toString()
}