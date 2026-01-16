# SBE4KT

This is an attempt to solve all my serialization related problems. SBE4KT is a Kotlin Compiler Plugin with a simple goal to eliminate the need for any manually written code to use  [SBE](https://github.com/real-logic/simple-binary-encoding) codecs. It uses your kotlin data classes as reference, builds intermediate representation of what your schema looks like, generates `sbe-schema.xml` in your resources folders, compiles them via `sbetool`, then generates number of high level kotlin extensions to use the codecs.

To use it you need to annotate a data class with `@SBE` annotation.



TODO : debugger no longer works
TODO : fix encode/decode statement into 4 smaller methods
TODO : fix MSG -> composite -> ValueClass complex uses


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

* All generated code is for kotlin, but if you need it to be java friendly use `SBE(javaFriendly=true)`
  * Java does not have cutting edge software innovations such as default arguments, type inference
  * Such methods are achieved trough overloading a methods with default arguments, which pollutes the namespace.


## Contributing

KSP api is very flexible, it is very likely that you may want to add some specific features for 
your own projects or modify the existing ones. If you do something that is generic and will benefit the library
please send a pull request.

Few things to keep in mind. Stay in line with the principles mentioned above. Many of these choices are deliberate
to discourage developers from misusing or building an unmaintainable mess.

Generated code has no warnings or comments, keep it that way if you are going to contribute, 
make the code self documenting, the method/field/variable names should be self explanatory, 
using long names is ok.

To debug the plugin while it is compiling your classes first invoke `ksp_debug_gradle` intellij configuration,
this runs gradle in a mode where it expects a debugger to attach, then run `ksp_debug_idea` to actually attach a debugger.
Now your breakpoints under `sbe4kt` module wont be ignored.