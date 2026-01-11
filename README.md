# SBE4KT

This is an attempt to solve all my serialization related problems. SBE4KT is a Kotlin Compiler Plugin with a simple goal to eliminate the need for any manually written code to use  [SBE](https://github.com/real-logic/simple-binary-encoding) codecs. It uses your kotlin data classes as reference, builds intermediate representation of what your schema looks like, generates `sbe-schema.xml` in your resources folders, compiles them via `sbetool`, then generates number of high level kotlin extensions to use the codecs.

To use it you need to annotate a data class with `@SBE` annotation.



There are some conventions and defaults that you should be aware of, most of these are optional, but they are made defaults for a reason (do not expect good performance if you choose to deviate).

* All fields of your class must be mutable.

* All fields must be NOT nullable.
  * SBE does not keep information if the field was set or not. 
  * If you absolutely want nullability use `@SBE(allowNullableTypes=true)`.

* All messages should be fixed sized. 
  * There are many useful types that you can use to avoid variable length fields.
  * If you need to use short strings try declaring custom charset and use `Str64`.
  * You can also use fixed sizes with Strings and repeating groups by using `@Meta(size=42)`.
  * If you absolutely want variable length fields use `@SBE(allowVarLengthFields=true)`.

* All fields are sorted in descending order to have effective memory use.
  * This means most 8 byte fields will naturally align. 
  * If you want to enforce padding you can use `@SBE(usePadding=true)`.

* All encode/decode generated methods give you opportunity to reuse buffers and instances.