package sbe4kt

/**
 * Main annotation/entry point for KSP to detect which classes to work with.
 * Data classes marked with this annotation will be picked up for code generation.
 */
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
   val version: Int = -1,

   /**
    * Adds offsets between fields to make sure each is at located at a multiple of 8 bytes,
    * Better for speed but consumes more space on wire.
    * Cannot be used in conjunction with `allowVarLengthFields`.
    */
   val usePadding: Boolean = false,

   /**
    * Will use a composite type.
    * SBE native feature.
    */
   val isStruct: Boolean = false,

   /**
    * Allows use of variable length fields.
    * Cannot be used with `usePadding`.
    * Will disable generation of flyweights for this type.
    */
   val allowVarLengthFields: Boolean = false,

   /**
    * Uses i64 bitset to keep track of nullability of fields and boolean flags.
    */
   val compactFlags: Boolean = false,

   /**
    * Adds a bitset which tracks which fields were actually set.
    * Up to 64 such fields can exist per message.
    */
   val allowNullableTypes: Boolean = false,
)