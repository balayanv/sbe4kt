package sbe4kt

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Message(
    /**
     * The unique template ID for this message.
     * If set to -1 (default), an ID will be automatically assigned starting from 1,
     * skipping any explicitly defined IDs.
     */
    val id: Int = -1,

    val version : Int = -1,

    /**
     * Adds offsets between fields to make sure each is at located at a multiple of 8 bytes,
     * this is beneficial for speed but consumes more space on wire.
     *
     * usePadding and allowVarLengthFields cannot be true at the same time.
     */
    val usePadding: Boolean = false,

    /**
     * Adds a bitset which tracks which fields were actually set.
     * Up to 64 such fields can exist per message.
     */
    @Deprecated("This adds extra computation costs to each get/set and adds bitset to the fields")
    val allowNullableTypes : Boolean = false,

    /**
     * Allows use of variable length fields. Impacts performance and functionality.
     */
    @Deprecated("This badly impacts performance, creates many limitations on how you can use this message type")
    val allowVarLengthFields : Boolean = false,

    /**
     * Uses a single bitset to track values of multiple boolean fields useful
     * if you have many boolean fields (adds few extra instructions per get/set).
     */
    val compactFlags : Boolean = false
)

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class Metadata(
   /**
    * Specify your own id for given field in sbe schema.
    */
   val id: Int = -1,

   val since : Int = -1,

   val isComposite : Boolean = false,

   /**
    * Allows to use fixed sized data structure for things like strings, and repeating groups.
    */
   val size : Int = -1,
)
