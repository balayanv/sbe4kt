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
    * SBE supports one of the two, by default its ASCII.
    */
   val encoding: Encoding = Encoding.ASCII,

   /**
    * Allowed types for a union field, must be a message or composite/struct.
    */
   val oneOf: Array<KClass<*>> = [],


   /**
    * Tags at which point measurements taken.
    * Order is matched with MicroTag4 slots.
    */
   val tags : Array<String> = [],

   /**
    * If positive number it is indicated that the field will not be available after specified schema version.
    */
   val removeAfter : Int = -1,
) {
   enum class Encoding(val sbeEncoding : String) {
      ASCII("US-ASCII"),
      UTF8("UTF-8"),
   }
}
