# SBE4KT

SBE4KT is a Kotlin Compiler Plugin with a simple goal to eliminate the need for any manually written code to use  [SBE](https://github.com/real-logic/simple-binary-encoding) codecs, it adds support for lots of features not available in SBE. 

It uses your kotlin data classes as reference, builds intermediate representation of what your schema looks like, generates `sbe-schema.xml` in your resources folders, compiles them via `sbetool`, then generates number of high level kotlin extensions to use the codecs.


## Getting started

To use it you need to annotate a data class with `@SBE` annotation.

TODO : demo all features here

## Recommendations, Conventions and Defaults

There are some conventions and defaults that you should be aware of, most of these are optional, but they are made defaults for a reason (do not expect good performance if you choose to deviate).

* Always define your message ids in real systems (autogen ids are for convenience only).
  * You can enforce it via `requireMessageIds`

* All fields of your class MUST be mutable.
  * Immutability is idiotic with very few exceptions.

* All fields must be NOT nullable
  * SBE does not keep information if the field was set or not
  * If you absolutely want nullability use `allowNullableTypes=true`
  * If you want to enforce non nulls use `requireNotNulls`
  * If you have many nullable fields and/or booleans use `compactFlags`

* All messages should be fixed sized. 
  * There are many useful types that you can use to avoid variable length fields
    * `FPDeci` for fixed point arithmetic fitting in `int64`
    * `TimeId` unique timestamps with least significant 2 digits used as tags
    * `Str64` custom charset string that uses single `int64` and implements `CharSequence`
    * fixed size strings (optionally with custom encodings)
    * fixed size collections (list/map/set)
  * If you absolutely want variable length fields use `allowVarLengthFields=true`
  * If you want to enforce fixed size use `requireFixedLength`

* All fields are sorted by size in descending order to have effective memory use
  * This means most 8 byte fields will naturally align
  * If you want to enforce padding you can use `usePadding=true`
    * this also adds padding to the end of the message

* All encode/decode generated methods give you opportunity to reuse buffers and instances

* All generated code is for kotlin foremost, but if you're still suffering with java use `javaFriendly=true`
  * Java does not have cutting edge software innovations such as default arguments, type inference
  * Such methods are achieved trough overloading a methods with default arguments, which pollutes the namespace


## Contributing

KSP api is very flexible, it is very likely that you may want to add some specific features for 
your own projects or modify the existing ones. If you do something that is generic and will benefit the library
please send a pull request.

Few things to keep in mind. Stay in line with the principles mentioned above. Many of these choices are deliberate
to discourage developers from misusing or building an unmaintainable mess.

Generated code has no warnings or comments, keep it that way if you are going to contribute, 
make the code self documenting, the method/field/variable names should be self-explanatory, 
using long names is ok.

To debug the plugin while it is compiling your classes first invoke `ksp_debug_gradle` intellij configuration,
this runs Gradle in a mode where it expects a debugger to attach, then run `ksp_debug_idea` to actually attach a debugger.
Now your breakpoints under `sbe4kt` module won't be ignored.

## TODO (serialization layer)

* add support for `size=N` for fixed size string, array, map, set
* add support for custom charset strings `str64`
* add tests for special value classes `FPDeci`, `TimeId`
* add support for fixed size custom charset str `StrN`
* add support for Sealed classes (unions)
* add demos for migration `version`, `since`
* add support for nesting @SBE messages
* add support for var length String/Blobs (disables flyweights)
* add support for `compactFlags` nullability + boolean flags stored via bitset `sbe:set`
* add support for `javaFriendly` 
* publish `sbe4kt` artefact to mvn local 
* add configuration options to `sbe4kt`
  * support `requireMessageIds` (fails compilation if a message has no id)
  * support `requireNotNulls` (cannot use `allowNullableTypes`)
  * support `requireFixedLength` (cannot use `allowVarLengthFields`)

## MISC TODO

* Slab byte[] allocators for buffer reuse
  * when you need many messages around instead of processing one at a time

* Extensions over Aeron IPC/UDP for GRPC like bidirectional communication
  * one to one (Req - Rep)
  * one to many (Req - IObservable<Rep>)
  * many to one (IObservable<Req> - Rep)
  * many to many (IObservable<Req> - IObservable<Rep>)

