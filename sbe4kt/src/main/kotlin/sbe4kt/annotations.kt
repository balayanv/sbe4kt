package sbe4kt

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SBE(
    /**
     * The unique template ID for this message.
     * SBE native attribute.
     */
    val id: Int = -1,

    /**
     * Version of the message.
     * SBE native attribute.
     */
    val version : Int = -1,

    /**
     * Adds offsets between fields to make sure each is at located at a multiple of 8 bytes,
     * Better for speed but consumes more space on wire.
     * Cannot be used in conjunction with `allowVarLengthFields`.
     */
    val usePadding: Boolean = false,

    /**
     * Adds a bitset which tracks which fields were actually set.
     * Up to 64 such fields can exist per message.
     */
    val allowNullableTypes : Boolean = false,

    /**
     * Allows use of variable length fields.
     * Cannot be used with `usePadding`.
     * Will disable generation of flyweights for this type.
     */
    val allowVarLengthFields : Boolean = false,

    /**
     * Uses i64 bitset to keep track of nullability of fields and boolean flags.
     */
    val compactFlags : Boolean = false
)

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class META(
   /**
    * Specify your own id for given field in sbe schema.
    * SBE native attribute.
    */
   val id: Int = -1,

   /**
    * Indicates since which version is this field available.
    * SBE native attribute.
    */
   val since : Int = -1,

   /**
    * Will use a composite type for this field.
    * SBE native feature.
    */
   val isComposite : Boolean = false,

   /**
    * Indicates to use fixed size for given field if possible (string/array/list/map/set).
    */
   val size : Int = -1,
)
