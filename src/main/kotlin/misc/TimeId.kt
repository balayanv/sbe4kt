package sbe4kt

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
        // 1. Strip the old tag (e.g., 12345 -> 12300)
        val base = (value / 100) * 100 
        // 2. Add the new tag
        return TimeId(base + newTag)
    }
    
    /**
     * Helper to replace the underlying timestamp completely.
     * Useful for chaining, e.g., time.set(newTime).tag(id)
     */
    fun set(newValue: Long): TimeId = TimeId(newValue)

    override fun toString(): String = value.toString()
}