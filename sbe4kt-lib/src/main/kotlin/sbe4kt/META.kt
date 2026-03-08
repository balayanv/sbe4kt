package sbe4kt

import kotlin.reflect.KClass

/**
 * Metadata annotation, used to provide additional context/customisation on field level.
 */
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
   val since: Int = -1,

   /**
    * Indicates to use fixed size for given field if possible (string/array/list/map/set).
    */
   val size: Int = -1,

   /**
    * Allowed types for a union field, must be a message or composite/struct.
    */
   val oneOf: Array<KClass<*>> = [],

   /**
    * Tags at which point measurements taken.
    * First element is the anchor field name (must be a TimeId field on the same message).
    * Remaining elements are milestone names that generate record/getTime methods.
    */
   val tags: Array<String> = [],

   /**
    * If positive number it is indicated that the field will not be available after specified schema version.
    */
   val removeAfter : Int = -1,
)
