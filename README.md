# The Problem

SBE is very simple in how it works but it exposes low level details of serialization to developers. 
Without experience, it inevitably creates codebases where you see encoders/decoders 
flying around in dependency injection frameworks, part of the serialization happening with one framework/manually and another part with SBE codecs. This creates fragile systems that are miserable to work with and completely defeats the purpose of SBE.

Also i do not like xml.

# SBE4KT

SBE4KT is a Kotlin Symbol Processor (a compiler plugin) with a simple goal : 
to eliminate the need for any manually written code to use  [SBE](https://github.com/real-logic/simple-binary-encoding) codecs. 

It adds support for lots of features not available in SBE via code generation, 
using SBE Encoder/Decoders as the building blocks to support the features we all need on serialziation layer.

It uses your classes as reference/schema, builds intermediate representation of what your schema looks like, 
then it generates `sbe-schema.xml` and compiles it via `sbetool`, 
and generates number of high level kotlin extensions to use the codecs.

The developers should never write custom code to do serialization. 
This library is intentionally kept simple such that its easy to customise to generate what you need/want. 
Even if its not already supported, its easy to change the code.


## Quick Start

To use it you need to annotate a data class with `@SBE` annotation.
You can try with a simple class `example.kt`

```kotlin
@SBE
data class Trade(
   var id : Long,
   var symbol : Int,
   var timestamp : Long,
   var price : Long,
   var amount : Long,
)
```
during compilation phase the plugin will detect all annotated classes, 
generate an internal representation of the overal schema from them `sbe-schema.xml`.

```xml
    <sbe:message name="Trade" id="1" description="Trade">
        <field name="id" id="1" type="int64"/>
        <field name="timestamp" id="2" type="int64"/>
        <field name="price" id="3" type="int64"/>
        <field name="amount" id="4" type="int64"/>
        <field name="symbol" id="5" type="int32"/>
    </sbe:message>
```
then it will be compiled via `sbe-tool` and a number of kotlin extensions 
will be generated in `TradeExtensions.kt` with signatures

```kotlin
// because our type has only fixed size properties compiler is able to determine its size
const val SBE_SIZE_TRADE = MessageHeaderEncoder.ENCODED_LENGTH + 36

// a helper method to create new instances of our type
@JvmOverloads
fun newTrade(
   id: Long = 0L,
   symbol: Int = 0,
   timestamp: Long = 0L,
   price: Long = 0L,
   amount: Long = 0L
): Trade

// a helper method to create new instances of our type with random values
@JvmOverloads
fun randomTrade(
   id: Long = Random.nextLong(),
   symbol: Int = Random.nextInt(),
   timestamp: Long = Random.nextLong(),
   price: Long = Random.nextLong(),
   amount: Long = Random.nextLong()
): Trade

// encode/decode methods that allow to reuse any instance/buffer and return nubmer of bytes encoded/decoded
fun encodeTrade(instance: Trade, buffer: UnsafeBuffer, offset: Int = 0): Int
fun decodeTrade(instance: Trade, buffer: UnsafeBuffer, offset: Int = 0): Int

// some extension functions for readability
fun Trade.encodeTo(buffer: UnsafeBuffer, offset: Int = 0): Int = encodeTrade(this, buffer, offset)
fun Trade.decodeFrom(buffer: UnsafeBuffer, offset: Int = 0): Int = decodeTrade(this, buffer, offset)

// 3 flyweight classes that allow to work directly with buffers without ever creating instances of our type
class TradeRW
class TradeW
class TradeR

// and a helper function to verify layout of our message on wire
fun Trade.printSbeLayout()
```

overall we should see tree of artifacts like this

```
build/generated/
├── ksp/
│   └── main/
│       ├── kotlin/
│       │   └── example/
│       │       └── TradeExtensions.kt          // kotlin codegen
│       └── resources/
│           └── sbe-schema.xml                  // sbe schema
└── sbe/
    └── main/
        └── java/
            └── sbe4kt/                         // sbetool output
                ├── MessageHeaderDecoder.java
                ├── MessageHeaderEncoder.java
                ├── MetaAttribute.java
                ├── package-info.java
                ├── TradeDecoder.java
                └── TradeEncoder.java
```

`sbe-schema.xml` can be added to your vcs to detect/verify changes.
Now with the generated code we can do things like this.

```kotlin
randomTrade().printSbeLayout()
```
which will print out a nice tabular breakdown of our message layout

```text
┌──────────────────────────────────────────────────────────────────────┐
│ Trade (totalSize = 44 bytes)                                         │
├────────┬───────────┬────────┬──────────┬──────┬──────────────────────┤
│ Offset │ Field     │ Type   │ SBE Type │ Size │ Value                │
├────────┼───────────┼────────┼──────────┼──────┼──────────────────────┤
│ 0      │ [HEADER]  │ Header │          │ 8    │ 36|1|1|0             │
│ 8      │ id        │ Long   │ int64    │ 8    │ -2637955432307753312 │
│ 16     │ timestamp │ Long   │ int64    │ 8    │ 6202968146653318647  │
│ 24     │ price     │ Long   │ int64    │ 8    │ -6199344808600958057 │
│ 32     │ amount    │ Long   │ int64    │ 8    │ -2680085302222566953 │
│ 40     │ symbol    │ Int    │ int32    │ 4    │ -53302703            │
└────────┴───────────┴────────┴──────────┴──────┴──────────────────────┘
```
We can see that our message is 44 bytes in size, 8 of which are used for headers (standard for sbe?),
also offsets and sizes of each field and actual values at the moment we printed this.

You'll notice that `symbol` field which was originally at second position was moved to the end.
Thre reason for that is alignment. Placing fields at multiples of 8 bytes is hardware friendly, 
and one of the easiest ways to achive such alignment is to put biggest fixed sized fields first. 

Otherwise all our fields after symbol would have been misaligned. 
Because it reordered it to last position, all our fields are aligned. 

To encode/decode data you can simply use the generated extension functions and never deal directly in SBE encoder/decoders.

```kotlin
   // lets create a random instance to serialize and deserialize
   val trade = randomTrade()

   // we can create correctly sized buffer for this type because we know the size at compile time
   val buffer = UnsafeBuffer(ByteArray(SBE_SIZE_TRADE))

   // to serialize all we have to do is
   encodeTrade(trade, buffer)
   // or we can use the extension function (which does the same thing)
   trade.encodeTo(buffer)

   // to deserialize we can follow exact same pattern
   val newTrade = newTrade()

   decodeTrade(newTrade, buffer)
   // or we can use the extension function (which does the same thing)
   newTrade.decodeFrom(buffer)

   // and we can verify that the instances are equal
   assert(trade == newTrade)
```

Note that at all points you are in complete control of what buffers and what instances are being used, 
you can (and should) use these methods to completely avoid generating any objects. 
Encoder/Decoders are actually very simple classes and can often be inlined as JIT does its magic.

But we can do much better. 

```kotlin
   // we can create a flyweight which directly works with the buffer 
   // all get/set operations are directly working with the buffer
   val fw = TradeRW()

   // we can directly write data to the buffer
   fw.id(1111)
   fw.timestamp(System.currentTimeMillis())
   fw.price(2222)
   fw.amount(3333)
   fw.symbol(42)
   
   // we can directly read any field from the buffer
   println(fw.id())
   println(fw.timestamp())
   println(fw.price())
   println(fw.amount())
   println(fw.symbol())
   
   // we can of course achieve the same as in encode/decode example before with flyweight
   // by writing contents of instance into buffer
   fw.encodeFrom(trade)

   // or reading the contents of the buffer into provided instance
   fw.decodeInto(trade)
   
   // we can of course point the flyweight to another buffer or offset at will
   // by default the flyweight will create correctly sized buffer if one isnt provided
   fw.wrap(buffer, 0)
```

Flyweights reuse the instances of encoders/decoders and they do less work compared to 
normal `encode/decode` functions because they avoid the `wrap()` that has to be performed on each invcation. 
Flyweight only has to `wrap()` the buffer once per jump. 

We also have specialized `ReadOnly/WriteOnly` variants of flyweights which further avoid excessive instructions. 
If all you do is read from a flyweight it makes no sense to do `encoder.wrap()`.

Flyweights are more efficient than `encode/decode` functions (which are still 0 garbage but do few more instructions).

These are the basics (the overall high level interface) of the library, do follow the instructions of [Tutorial](#full-tutorial) section to get familiar with [all the features](#core-features) of the library.

---
## Recommendations, Conventions and Defaults

There are some conventions and defaults that you should be aware of, most of these are optional, but they are made defaults for a reason (do not expect good performance if you choose to deviate).

* Always define your message ids in real systems (autogen ids are for convenience only)
  * You can enforce it via `requireMessageIds`

* All fields of your class MUST be mutable.
  * no java `records`, immutability is idiotic/disasterous for performance (with very few exceptions)

* All fields are PREFERRED to be NOT nullable
  * SBE does not keep information if the field was set or not
  * If you absolutely want nullability use `allowNullableTypes=true`
  * If you have many nullable fields and/or booleans you SHOULD use `compactFlags`
  * I RECOMMEND enforce non nulls via `requireNotNulls` at module level

* All messages are PREFERRED be fixed sized.
  * The tradoff of functionality and performance for using var length fields is not worth it
  * There are many useful types that you can use to avoid variable length fields
    * `FPDeci` for fixed point arithmetic fitting in `int64`
    * `TimeId` unique timestamps fitting in `int64` that can be unique ids
    * `str64` uses custom charset defined by you and fits in a single `int64` and implements `CharSequence`
    * fixed size strings (when `str64` is not enough)
    * fixed size collections (list/map/set/arrays)
  * If you absolutely want variable length fields use `allowVarLengthFields=true`
  * I RECOMMEND enforce fixed size only via `requireFixedLength` at module level

* If you want to enforce padding you can use `usePadding=true`
  * this also adds padding to the end of the message, so you can append many in one buffer

* All encode/decode generated methods allow you to reuse both instances and buffers

* All generated code is for kotlin primarily, but if you still have to endure java use `javaFriendly=true`
  * For example java does not have cutting edge software innovations such as default arguments (those are very very difficult for java language developers to implement)
  * Hence to be able to provide simple no argument `new$Type, random$Type` we have to create overloads of those functions
  * This pollutes the namespace and can create issues if you have many annotated messages.

* Best serialization is NO serialization, since Flyweights allow you to directly interact with buffer, there is 0 time spent serializing/deserializing.
  * Your message is ready to work with as soon as its in the buffer, and its ready to be sent at any point
  * Flyweights are fantastic in cases where your business logic doesnt actually do many get/sets or only needs to access fraction of the messages content.
  * The performance difference of not doing any serialization at all can be immense.
  * Even if you do need to `encode/decode` into objects using flyweight api and reusing objects should be slightly faster than `encode/decode` directly.
  * Consider this the recommended default.

* Summery
  * Fixed sized fields/messages everywhere (its not that hard, see in the full tutorial)
  * Discourage nullability
  * Directly read/write to buffers, no need to allocate objects at all
  * Prefer structs (composites) over `sbe:message`es


---
## Full Tutorial

Make sure you have explored [Quick Start](#quick-start) section before going trough this tutorial. The examples here are deliberately not in source so you try these yourself. 
It wont take much time to go trough the full tutorial, so i encourage you to do so. 

### Alignment/Padding

Alignment is very useful when you are willing to get better performance over compact memory layout.
By default fields will be ordered in descending order by size, this naturally achieves alignment for most of the fields.

```kotlin
@SBE
data class Msg(var f1: Byte, var f2: Int, var f3: Long)

fun main() { randomMsg().printSbeLayout() }
```
You should see that the order is different than what was specified in definition.

```text
┌────────────────────────────────────────────────────────────────────┐
│ Msg (totalSize = 21 bytes)                                         │
├────────┬──────────┬────────┬──────────┬──────┬─────────────────────┤
│ Offset │ Field    │ Type   │ SBE Type │ Size │ Value               │
├────────┼──────────┼────────┼──────────┼──────┼─────────────────────┤
│ 0      │ [HEADER] │ Header │          │ 8    │ 13|1|1|0            │
│ 8      │ f3       │ Long   │ int64    │ 8    │ 8356817513295047039 │
│ 16     │ f2       │ Int    │ int32    │ 4    │ 793399309           │
│ 20     │ f1       │ Byte   │ int8     │ 1    │ -70                 │
└────────┴──────────┴────────┴──────────┴──────┴─────────────────────┘
```

But we can see that `f1` is at the last position and is not aligned (beacuse `f2` has size of 4).
For any `@SBE` annotated type you can use padding to achive alignment by using `usePadding=true` flag.

```kotlin
@SBE(usePadding = true)
data class Msg(var f1: Byte, var f2: Int, var f3: Long)
```

Now you will see that all fields are aligned to multiple of 8 bytes and message ends with a padding.
This is done such that next message when written after this will start at aligned position.

Original message without alignment took 21 bytes of space while with padding it takes 32.

```text
┌──────────────────────────────────────────────────────────────────────┐
│ Msg (totalSize = 32 bytes)                                           │
├────────┬───────────┬────────┬──────────┬──────┬──────────────────────┤
│ Offset │ Field     │ Type   │ SBE Type │ Size │ Value                │
├────────┼───────────┼────────┼──────────┼──────┼──────────────────────┤
│ 0      │ [HEADER]  │ Header │          │ 8    │ 24|1|1|0             │
│ 8      │ f3        │ Long   │ int64    │ 8    │ -7207667731427379597 │
│ 16     │ f2        │ Int    │ int32    │ 4    │ 1248514884           │
│ 20     │ [PADDING] │        │          │ 4    │                      │
│ 24     │ f1        │ Byte   │ int8     │ 1    │ 9                    │
│ 25     │ [PADDING] │        │          │ 7    │                      │
└────────┴───────────┴────────┴──────────┴──────┴──────────────────────┘
```
The sbe schema will be changing accordingly to use offset attribute to achieve alignment. There are no extra fields used to create artificial spacing. Just offsets and allocations of the buffer space.

```xml
<sbe:message name='Msg' id='1' blockLength='24' description='Msg'>
   <field name='f3' id='3' type='int64' offset='0'/>
   <field name='f2' id='2' type='int32' offset='8'/>
   <field name='f1' id='1' type='int8' offset='16'/>
</sbe:message>
```

Note that `blockLength` does not include the header size (which is 8).

### Enums

When you use enums the generated sbe schema will use sbe enum primitives and convert accordingly 
(i might change this in the future to avoid declaring two enums, instead just store it as byte or int).

```kotlin
enum class SomeEnum { DEFAULT, VAlUE1, VALUE2 }

@SBE
data class Msg(var f1: SomeEnum)
```
This will generate

```xml
<enum name='SomeEnum' encodingType='uint8'>
   <validValue name='DEFAULT'>0</validValue>
   <validValue name='VAlUE1'>1</validValue>
   <validValue name='VALUE2'>2</validValue>
</enum>
```

### Value classes 

You can use value clases as well.

```kotlin
@JvmInline
value class SomeValueClass(val underlying : Int)

@SBE
data class Msg(var f1: SomeValueClass)
```
Because value classes effectively inline into whatever underlying field they have 
at SBE/bytecode level its simply an int.

```xml
<sbe:message name='Msg' id='1' description='Msg'>
   <field name='f1' id='1' type='int32'/>
</sbe:message>
```

The generated code we preserve it and "convert" accordingly.
This is an important feature that will allow us to efficiently work with fixed point decimals, 
fixed strings and do many other tricks later...

```text
┌───────────────────────────────────────────────────────────────────────────────────────────────┐
│ Msg (totalSize = 12 bytes)                                                                    │
├────────┬──────────┬────────────────┬──────────┬──────┬────────────────────────────────────────┤
│ Offset │ Field    │ Type           │ SBE Type │ Size │ Value                                  │
├────────┼──────────┼────────────────┼──────────┼──────┼────────────────────────────────────────┤
│ 0      │ [HEADER] │ Header         │          │ 8    │ 4|1|1|0                                │
│ 8      │ f1       │ SomeValueClass │ int32    │ 4    │ SomeValueClass(underlying=-1773498720) │
└────────┴──────────┴────────────────┴──────────┴──────┴────────────────────────────────────────┘
```

### Type Aliases and nesting 

Type aliases require no special support since they just get inlined.
We can nest various types, since actual code generation is delegated and resolved recursively.

You can nest them however you like. For demonstration lets use a Value that points to 
another Value type that points to a type alias that points to an enum.

```kotlin
enum class SomeEnum { DEFAULT, VAlUE1, VALUE2 }

typealias TheEnumAlias = SomeEnum

@JvmInline
value class SomeValueClass(val underlying : TheEnumAlias)

@JvmInline
value class SomeOtherValueClass(val underlying : SomeValueClass)

@SBE
data class Msg(var f1: SomeOtherValueClass)
```

We can see that actual type is just an enum taking 1 byte. 
While the value being printed has all the value wrappers present.

```text
┌───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Msg (totalSize = 9 bytes)                                                                                                     │
├────────┬──────────┬─────────────────────┬──────────┬──────┬───────────────────────────────────────────────────────────────────┤
│ Offset │ Field    │ Type                │ SBE Type │ Size │ Value                                                             │
├────────┼──────────┼─────────────────────┼──────────┼──────┼───────────────────────────────────────────────────────────────────┤
│ 0      │ [HEADER] │ Header              │          │ 8    │ 1|1|1|0                                                           │
│ 8      │ f1       │ SomeOtherValueClass │ SomeEnum │ 1    │ SomeOtherValueClass(underlying=SomeValueClass(underlying=VALUE2)) │
└────────┴──────────┴─────────────────────┴──────────┴──────┴───────────────────────────────────────────────────────────────────┘
```

The generated code naturally handles it correctly.

```kotlin
fun f1() = SomeOtherValueClass(SomeValueClass(SomeEnum.entries[decoder.f1().ordinal]))
```

It is important to keep the support for different types generic. `IR.Leaf` types have granular interface methods,
that describe how to extract a value, how to convert your value to sbe and back etc. 
Code generation takes advantage of these generic methods to compose statements and recursion does the rest.

### Flyweights

Flyweights in this library have 2 major objectives.
* Directly read/write to buffer with as few instructions as possible.
* Avoid any allocations while doing the above (optional)

Looking at `Trade` example we can see what the generated flyweight really does when we work with it.

```kotlin
   fun id(id: Long) = apply { encoder.id(id) }
   fun symbol(symbol: Int) = apply { encoder.symbol(symbol) }
   fun timestamp(timestamp: Long) = apply { encoder.timestamp(timestamp) }
   fun price(price: Long) = apply { encoder.price(price) }
   fun amount(amount: Long) = apply { encoder.amount(amount) }

   fun encodeFrom(source: Trade) = apply {
      encoder.id(source.id)
      encoder.timestamp(source.timestamp)
      encoder.price(source.price)
      encoder.amount(source.amount)
      encoder.symbol(source.symbol)
   }
```

It no longer does any `wrap()` because it is already correctly positioned. 

Since you know exact sizes of message types (if you follow the recommended approach). 
You can create ring buffers which have flyweights of given union type, and you can directly read write.

There are 3 variations of these flyweights `RW`, `R`, `W`.
So if you have a different type of worker that mostly reads or writes or you do not want to expose an interface that can write, 
then you have an option to use the correct flyweight. 

Note they are completely different classes, you cannot cast `RW` into `R/W`.

When using these flyweights your buffers are your ojbects, there is no actual serialization going on.

### Packed Strings with custom encoding (str64)

When working with a string field you can save on space by defining custom charset of exactly what characters are allowed. 
For short strings this allows to pack the entire value of the string into a 64 bit field.

This is achieved trough user defined charsets.

```kotlin
object Symbol : CharSet("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_")
```

This effectively creates a unique charset singletone, 
the KSP processor will generate a value class `SymbolStr64` specific to this codec.
Now you can use your custom encoding to store short strings like `XBTUSDTH26` (a btc/usd futures instrument)
into as single `int64` field.

```kotlin
fun main() {
   val symbol = SymbolStr64.encode("XBTUSDTH26")
   println(symbol.toDebugBinaryString())
}
```

This will print detailed information about your string.

```text
XBTUSDTH26 (10 characters) CodecStr64(charset=CharSet(chars='ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_'), maxCap=10, bitWidth=6)
[0000 100001 101110 000100 001010 001000 110010 101010 001010 010000 000110]
```
From this string you can see that your charset allows for maximum 10 characters.
`Str64` codec uses null termination to know if the string ended, but if the value is long enough all bits are used for the value. 

We can see that the last 4 bits are unused (wasted), this is because our `bitWidth=6`, so it takes 6 bits to encode a single character, thus we only use 60 bits.

If we use smaller codec we can fit a lot more. 

```kotlin
object Symbol : CharSet("ABCDEFGHIJKLMNO")

fun main() {
   val symbol = SymbolStr64.randomStr()
   println(symbol.toDebugBinaryString())
}
```
Now you can work with more characters and you have no wasted bits.

```text
LJHNBEGIDMCGONAC (16 characters) CodecStr64(charset=CharSet(chars='ABCDEFGHIJKLMNO'), maxCap=16, bitWidth=4)
[1100 1000 0111 1111 1110 1100 1011 0010 1001 1110 1010 0100 0111 0001 0101 0011]
```
The debug string automatically splits the value into `bitWidth` chunks, hence we see 4 bits before space.
Each chunk is one character. There is also no null terminator `0000` because we used max available capacity.

Because this generated type `SymbolStr64` is a value class it means we can use it just like any other value class.
Now our trade example can be improved.

Also because this value type is unique to your encoding, at compile time you cannot mix it with some other value type string,
even though both of them are `long`s they are not interchanable (typealiases are). 

Lets use it in our `Trade` message type.

```kotlin
object Symbol : CharSet("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_")

@SBE
data class Trade(
   var id: Long,
   var symbol: SymbolStr64,
   var timestamp: Long,
   var price: Long,
   var amount: Long,
)

fun main() {
   randomTrade().printSbeLayout()
   println("max characters in symbol : ${SymbolStr64.codec.maxCap}")
}
```

SBE simply stores an `int64` while all our interfaces use `SymbolStr64` (which also implements CharSequence)...

```text
┌──────────────────────────────────────────────────────────────────────────┐
│ Trade (totalSize = 48 bytes)                                            │
├────────┬───────────┬─────────────┬──────────┬──────┬─────────────────────┤
│ Offset │ Field     │ Type        │ SBE Type │ Size │ Value               │
├────────┼───────────┼─────────────┼──────────┼──────┼─────────────────────┤
│ 0      │ [HEADER]  │ Header      │          │ 8    │ 40|1|1|0            │
│ 8      │ id        │ Long        │ int64    │ 8    │ 2081820661654400732 │
│ 16     │ symbol    │ SymbolStr64 │ int64    │ 8    │ BWNC7NLBWT          │
│ 24     │ timestamp │ Long        │ int64    │ 8    │ 555450705016411898  │
│ 32     │ price     │ Long        │ int64    │ 8    │ 3478299830261472651 │
│ 40     │ amount    │ Long        │ int64    │ 8    │ 889343814827555264  │
└────────┴───────────┴─────────────┴──────────┴──────┴─────────────────────┘
max characters in symbol : 10
```

### Fixed length Strings (strN)

Normally 10 characters is more than enough for most symbols,
but if you know you need more you can use `@META` annotation with `String`.

```kotlin
@SBE
data class Trade(
   var id: Long,
   @META(size = 20)
   var symbol: String,
   var timestamp: Long,
   var price: Long,
   var amount: Long,
)
```

In which case it will use a `char[]` of size 20 in SBE schema like this.

```xml
<type name='CharArray20' primitiveType='char' length='20' characterEncoding='US-ASCII'/>
```
By default SBE uses ASCII (1 byte per character).

```text
┌─────────────────────────────────────────────────────────────────────────┐
│ Trade (totalSize = 60 bytes)                                           │
├────────┬───────────┬────────┬─────────────┬──────┬──────────────────────┤
│ Offset │ Field     │ Type   │ SBE Type    │ Size │ Value                │
├────────┼───────────┼────────┼─────────────┼──────┼──────────────────────┤
│ 0      │ [HEADER]  │ Header │             │ 8    │ 52|1|1|0             │
│ 8      │ symbol    │ String │ CharArray20 │ 20   │ PFx7myIFrKfhHVCtMY5V │
│ 28     │ id        │ Long   │ int64       │ 8    │ 7562228614901166723  │
│ 36     │ timestamp │ Long   │ int64       │ 8    │ -2750041358410479077 │
│ 44     │ price     │ Long   │ int64       │ 8    │ -2421667256842112630 │
│ 52     │ amount    │ Long   │ int64       │ 8    │ 7996860305436213283  │
└────────┴───────────┴────────┴─────────────┴──────┴──────────────────────┘
```

But you can also use UTF-8.

```kotlin
@SBE
data class Trade(
   var id: Long,
   @META(size = 20, encoding = UTF8)
   var symbol: String,
   var timestamp: Long,
   var price: Long,
   var amount: Long,
)
```

To avoid collisions it will add a `U` next to the type to indicate the encoding.
`CharArray20U means (char[20] UTF8)` while `CharArray20 means (char[20] ASCII)`.

```text
┌──────────────────────────────────────────────────────────────────────────┐
│ Trade (totalSize = 60 bytes)                                            │
├────────┬───────────┬────────┬──────────────┬──────┬──────────────────────┤
│ Offset │ Field     │ Type   │ SBE Type     │ Size │ Value                │
├────────┼───────────┼────────┼──────────────┼──────┼──────────────────────┤
│ 0      │ [HEADER]  │ Header │              │ 8    │ 52|1|1|0             │
│ 8      │ symbol    │ String │ CharArray20U │ 20   │ v4FFw4v5ltcr7UYunGoz │
│ 28     │ id        │ Long   │ int64        │ 8    │ 5641499793141507981  │
│ 36     │ timestamp │ Long   │ int64        │ 8    │ -279987649001383009  │
│ 44     │ price     │ Long   │ int64        │ 8    │ -6156697613066877079 │
│ 52     │ amount    │ Long   │ int64        │ 8    │ -1915736276978719424 │
└────────┴───────────┴────────┴──────────────┴──────┴──────────────────────┘
```

Downside of using String is that your flyweight types create garbage when you invoke the getter of the field.

TODO : this is something that needs to be addressed via some type that can wrap a buffer and represent it as CharSequence (i'm sure there is soemthing like that out there).
TODO : alternative use CharSequence then flyweight can keep a reusable StringBuilder which will be populated on each get... need to think whats the best approach.

### Fixed Point Decimal Arithmetic (FPDN)

We can further improve our example by using one of our FPD types.
There are 2 kinds of these in `FixedPointDecimal.kt`.

The simplest kind is the static fixed point decimals (FPDN where N is number from 0-15). 
These are equivalent of using `long` in trading systems with implicit knowledge of what the scale is. 
Problem of that approach is that its easy to make mistakes such as mathematical operations 
whith values that actually have different scale (and its hard to read the code). 
Value classes help us with this.

Lets look at FPD5 for example (a fixed point decimal with implied scale of 5 decimal points).

```kotlin
// i use such type aliases to make it easier to use while not limiting myself to a "compact" less descriptive name
typealias FPD5 = FixedPointDecimal5

@JvmInline
value class FixedPointDecimal5(val value: Long)
```
Using it is quite simple. In general any method with `Exact` suffix will do extra checks and throw exceptions if something is wrong. 
Non exact methods are the unsafe variants. 
You should make sure that any kind of validation is done before those are invoked.

```kotlin
val f5 = FPD5.fromExact("123.456789")
println(f5)
println(f5.debugToString())
println(f5.debugValueRange())
```
I find `debugValueRange()` useful for developers who are new to FPD types to accurately gauge which scale to use
and what limitations to expect.

```text
123.45678
123.45678, scale : 5
123.45678, scale : 5, range : [-92233720368547.75808, 92233720368547.75807]
```
You'll see that it cuts away the extra data after precision 5 which is expected.
It always has scale of 5 (its implicit but not stored anywhere).
Its range is the full range of int64. 
Because its an inline class it is in actuallity a single int64, hence all operations with it are as cheap as long to long operations. 

Lets do some addition/subtraction with these.

```kotlin
val a = FPD5.fromExact("1111.11111")
val b = FPD5.fromExact("22.22222")
val c = a + b
println(a.debugToString())
println(b.debugToString())
println(c.debugToString())
```

This will produce an expected output.

```text
1111.11111, scale : 5
22.22222, scale : 5
1133.33333, scale : 5
```

The important part is that no actual objects are used, 
if we decompile the bytecode back to java we will see few local `int64` variables and some static methods to operate on them.

```java
long a = FixedPointDecimal5.Companion.fromExact-VqRYOb4((CharSequence)"1111.11111");
long b = FixedPointDecimal5.Companion.fromExact-VqRYOb4((CharSequence)"22.22222");
long c = FixedPointDecimal5.plus-40zh378(a, b);
System.out.println(FixedPointDecimal5.debugToString-impl(a));
System.out.println(FixedPointDecimal5.debugToString-impl(b));
System.out.println(FixedPointDecimal5.debugToString-impl(c));
```

The implementation of it is simple addition since we know both are of the same scale.

```kotlin
operator fun plus(other: FPD5) = FPD5(value + other.value)
```
Unlike using raw `long` if we attempt to add/subtract two different FPD types we will fail at compile time.

```kotlin
val a = FPD5.fromExact("1111.11111")
val b = FPD4.fromExact("22.22222")
val c = a + b // fails to compile
```

Note that if you use value type in generics (some collections etc) jvm will have to use an actual object wrapper.
You can easily see such cases by decompiling into bytecode. Value classes are agressively inlined whenever possible.


### Packed FixedPointDecimal (FPD)

Downside of using hardcoded `FPDN` types is that you are forced to carry this implicit scale into wrie format.
This is where the other variant of FPD is handy : PackedFixedPointDecimal (or just FPD)

```kotlin
typealias FPD = PackedFixedPointDecimal

@JvmInline
value class PackedFixedPointDecimal(val packed: Long) : Comparable<FPD>
```

This is a Packed variant of FPD where 4 bits are used for the position of the decimal point, 
and 60 bits are used for the value. It effectively trades a bit of performance (3 instructions vs 1) and range for flexibility.

It is very close to performance of using normal `long` or `FPDN` types 
but it is more dynamic beacuse it can automatically convert (upscale) lower scale value to get operations done. 

If you run the same example but using packed FPD you will NOT see issues at compile time.

```kotlin
val a = FPD.fromExact("1111.11111")
val b = FPD.fromExact("22.222")
val c = a + b
//   val c = a.plusExact(b) // this will throw exception if we overflow
println(a.debugToString())
println(b.debugToString())
println(c.debugValueRange())
```

This will correctly upsacle the `b` variable then perform sum operation.
You will also notice that we have smaller range, but its still enough for all financial usecases (when used correctly).
Be careful when operating at very high scale, since your range is narrower.

```text
1111.11111, scale : 5
22.222, scale : 3
1133.33311, scale : 5, range : [-5764607523034.23488, 5764607523034.23487], usableBits : 60
```

So now we can improve our Trade Type further.

```kotlin
@SBE
data class Trade(
   var id: Long,
   var symbol: SymbolStr64,
   var timestamp: Long,
   var price: FPD,
   var amount: FPD,
)
```

Now we have percise arithmetic in trade class.

```text
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ Trade (totalSize = 48 bytes)                                                         │
├────────┬───────────┬─────────────────────────┬──────────┬──────┬──────────────────────┤
│ Offset │ Field     │ Type                    │ SBE Type │ Size │ Value                │
├────────┼───────────┼─────────────────────────┼──────────┼──────┼──────────────────────┤
│ 0      │ [HEADER]  │ Header                  │          │ 8    │ 40|1|1|0             │
│ 8      │ id        │ Long                    │ int64    │ 8    │ 2125489629857427912  │
│ 16     │ symbol    │ SymbolStr64             │ int64    │ 8    │ F5UQ6OR4FL           │
│ 24     │ timestamp │ Long                    │ int64    │ 8    │ 5414516759342752367  │
│ 32     │ price     │ PackedFixedPointDecimal │ int64    │ 8    │ -203.837602162960252 │
│ 40     │ amount    │ PackedFixedPointDecimal │ int64    │ 8    │ 412044480628.449789  │
└────────┴───────────┴─────────────────────────┴──────────┴──────┴──────────────────────┘
```
For now all FPD types are signed, but its trivial to make unsigned variants.
Kotlin already has `UByte`, `UInt`, `ULong` value classes, a similar approach can be used here.

### UUIDs and timestamps

We can use `TimeId` value type to capture sequential timestamps that are also unique (hence can act as ID).
Like other utility types this one is also a value class wrapping an `int64`.

To do this we can use the same approach as [chronicle distributed timestamps](https://chronicle.software/unique-timestamp-identifiers/). 
We are going to use nanoscale timestamps but use last 2 digits of them as identifiers for the current application. 
If we make our timestamp generation unique on application level (which is trivial), the last 2 digits guarantee that its unique across many services (99 of them).

In case of chronicle the id/tag is used for unique host (and random access file is used to CAS the value for any service running on the same host). 
This type keeps it simple (it works per application, not host).

In my experience I hardly ever need more than 99 services in "core"... 
But its easy to modify this type to give you range of 999 
or use agrona equivalent to achieve same host level uniqueness.

Using `TimeId` is quite trivial, here is a simple example demonstrating how it works 
when we rapidly generate new ids (more frequently than our clock).

Note if you want to take full advantage of the nanotime you need to use a good system clock, 
obviously do not use `System.nanotime()`.

```kotlin
fun main() {
   fun sleepSometimes(){
      if (ThreadLocalRandom.current().nextDouble(0.0, 1.0) < 0.01) {
         println("sleep")
         Thread.sleep(100)
      }
   }

   var id = TimeId().ms(System.currentTimeMillis()).tag(77)
   repeat(1000) {
      
      sleepSometimes()                             // this is so that we can force a clock progress
      id = id.nextMs(System.currentTimeMillis())   // we preserve the tag as we create new timestamps
      println(id.debugToString())
   }
}
```

In this example i'm using `System.currentTimeMillis()` loosing out on precision of the timestamps.

```text
1769954956889-0000(77)
1769954956889-0001(77)
1769954956889-0002(77)
1769954956889-0003(77)
1769954956889-0004(77)
1769954956889-0005(77)
1769954956890-0000(77)
1769954956890-0001(77)
1769954956890-0002(77)
1769954956890-0003(77)
1769954956890-0004(77)
1769954956890-0005(77)
1769954956890-0006(77)
sleep
1769954956993-0000(77)
1769954956993-0001(77)
1769954956993-0002(77)
1769954956993-0003(77)
1769954956993-0004(77)
1769954956993-0005(77)
```
As you can see, since our clock is quite slow, we generated 5 unique ids between milliseconds `1769954956889` and `1769954956890`. 
We simply increment to the next value when our clock does not give us newer value.
The intentional sleep there is to force our clock to reset the "counter".

We have 10 million unique ids per second and can generate these until April 11, 2262.
More than enough. Also nothing stops us from "reseting" the starting point of time since value class encapsulates it (so even 2262 is not really a limit).

So we could easily make our TimeId be the nanoseconds since 2020 and do some simple adjustments when getting unix time etc...

As other value types this is a simple `long`, and perfectly valid timestamp at all times. 

We can use this to further improve our trade type. 
We no longer need a separate id and a timestamp.

```kotlin
   val trade = Trade(
      id = TimeId().ms(System.currentTimeMillis()).tag(77),
      symbol = SymbolStr64.from("BTC_USD"),
      price = FPD.fromExact("77955.14"),
      amount = FPD.fromExact("4.23"),
   )

   trade.printSbeLayout()
```
We have very lean and already aligned structure.

```text
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ Trade (totalSize = 40 bytes)                                                        │
├────────┬──────────┬─────────────────────────┬──────────┬──────┬─────────────────────┤
│ Offset │ Field    │ Type                    │ SBE Type │ Size │ Value               │
├────────┼──────────┼─────────────────────────┼──────────┼──────┼─────────────────────┤
│ 0      │ [HEADER] │ Header                  │          │ 8    │ 32|1|1|0            │
│ 8      │ id       │ TimeId                  │ int64    │ 8    │ 1544564162267732907 │
│ 16     │ symbol   │ SymbolStr64             │ int64    │ 8    │ YQCSTTZDLW          │
│ 24     │ price    │ PackedFixedPointDecimal │ int64    │ 8    │ 300171.874819729017 │
│ 32     │ amount   │ PackedFixedPointDecimal │ int64    │ 8    │ 520823266601151039  │
└────────┴──────────┴─────────────────────────┴──────────┴──────┴─────────────────────┘
```

If we were to use a very compact json for this and efficient ASCII encoding.

```json5
{"id":"1544564162267732907","symbol":"YQCSTTZDLW","price":"300171.874819729017","amount":"520823266601151039"}
```

We would have `110` bytes instead of `40` (63% more compact), 
and we would waste time serializing/deserializing this vs 0 serialization needed with SBE.

### Recording packed timestamps MicroTag4

Lets assume we know the starting timestamp of an event we want to track as it travels trough various services.
We could use a single `int64` to record 4 `uint16` durations (representing microseconds), 
each one would have maximum value of `65535` microseconds or `65` milliseconds. 

This means that if we have a starting timestamp and none of our measurements after that are more than 65ms away 
we could measure `260ms` range with microsecond precision 4 times only using single `int64`.

For a low latency system this range is more than enough, its enough for most regular systems too. 
Lets demonstrate how simple `MicroTag4` value type would work.

```kotlin
fun main() {
   val timer = MicroTag4(Long.MAX_VALUE)
      .set1(11.us)
      .set2(22.us)
//      .set3(33.us)
      .set4(45.us)

   println(timer.toDebugString())
}
```
This will give us the following output.

```text
binary : [1011010000000000 1111111111111111 0110100000000000 1101000000000000]
values : [11, 22, --, 45]
```
Note that `--` here means that either value was never recorded (maybe due to a bug or clocks are drifted apart) or it was more than 65ms... either way in such case anything beyond that value is not usable since each next value is relative to previous one (45 is a throwaway value).

Now with this utility we can take 1 nanosecond precision and 4 microsecond precision timestamps of our Trade event as it travels trough processes.

```kotlin
@SBE
data class Trade(
  var id: TimeId,
  @META(tags = ["id", "PublishedToExhaust", "DeserializedOnMDS", "PublishedViaWS", "MarkedConsumed"])
  var tags: MicroTag4,
  var symbol: SymbolStr64,
  var price: FPD,
  var amount: FPD,
)

fun main() {
  val time = TimeId.now().tag(77)

  val trade = newTrade(
    id = time,
    tags = MicroTag4(),
    symbol = SymbolStr64.from("BTC_USD"),
    price = FPD.from(78000),
    amount = FPD.from(1),
  )

  "SIMULATING A TRADE EVENT GOING TROUGH VARIOUS PROCESSES IN A SYSTEM".log()
  "trade event was created                   : $trade".log()
  "trade event leaving the exhaust service   : ${trade.recordPublishedToExhaust(time.add(10.us))}".log()
  "trade event deserialized on MDS           : ${trade.recordDeserializedOnMDS(time.add(20.us))}".log()
  "trade event published via WS              : ${trade.recordPublishedViaWS(time.add(30.us))}".log()
  "trade event was marked consumed           : ${trade.recordMarkedConsumed(time.add(50.us))}".log()

  "CHECKING IF RECORDED PACKED TIME DIFFS UNPACK INTO PROPER TIMESTAMPS".log()
  "getTimePublishedToExhaust  : ${trade.getTimePublishedToExhaust()}".log()
  "getTimeDeserializedOnMDS   : ${trade.getTimeDeserializedOnMDS()}".log()
  "getTimePublishedViaWS      : ${trade.getTimePublishedViaWS()}".log()
  "getTimeMarkedConsumed      : ${trade.getTimeMarkedConsumed()}".log()

  trade.printSbeLayout()
}
```

The code generator uses the first `id` to know which field to use as the `anchor`. 
Second value `PublishedToExhaust` uses `id` to build its timestamp, 
3rd value/tag `DeserializedOnMDS` uses `PublishedToExhaust` as its anchor. 
Each depends on previous one so order matters. 

If one of these timestamps is more than 65ms away or never set then telemetry systems should be smart enough 
to discard or register an issue associated with this.

We could create some parameter that instead of using previous tag to build next one uses id as anchor for each tag.
In that case order does not matter but maximum measurement distance in time is 65ms (still forever in microsecond latency system).


```text
SIMULATING A TRADE EVENT GOING TROUGH VARIOUS PROCESSES IN A SYSTEM
trade event was created                   : Trade(id=1770225351100000077, tags=[0, 0, 0, 0], symbol=BTC_USD, price=78000, amount=1)
trade event leaving the exhaust service   : Trade(id=1770225351100000077, tags=[10, 0, 0, 0], symbol=BTC_USD, price=78000, amount=1)
trade event deserialized on MDS           : Trade(id=1770225351100000077, tags=[10, 10, 0, 0], symbol=BTC_USD, price=78000, amount=1)
trade event published via WS              : Trade(id=1770225351100000077, tags=[10, 10, 10, 0], symbol=BTC_USD, price=78000, amount=1)
trade event was marked consumed           : Trade(id=1770225351100000077, tags=[10, 10, 10, 20], symbol=BTC_USD, price=78000, amount=1)
CHECKING IF RECORDED PACKED TIME DIFFS UNPACK INTO PROPER TIMESTAMPS
getTimePublishedToExhaust  : 1770225351100010077
getTimeDeserializedOnMDS   : 1770225351100020077
getTimePublishedViaWS      : 1770225351100030077
getTimeMarkedConsumed      : 1770225351100050077
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ Trade (totalSize = 48 bytes)                                                        │
├────────┬──────────┬─────────────────────────┬──────────┬──────┬─────────────────────┤
│ Offset │ Field    │ Type                    │ SBE Type │ Size │ Value               │
├────────┼──────────┼─────────────────────────┼──────────┼──────┼─────────────────────┤
│ 0      │ [HEADER] │ Header                  │          │ 8    │ 40|1|1|0            │
│ 8      │ id       │ TimeId                  │ int64    │ 8    │ 1770225351100000077 │
│ 16     │ tags     │ MicroTag4               │ int64    │ 8    │ [10, 10, 10, 20]    │
│ 24     │ symbol   │ SymbolStr64             │ int64    │ 8    │ BTC_USD             │
│ 32     │ price    │ PackedFixedPointDecimal │ int64    │ 8    │ 78000               │
│ 40     │ amount   │ PackedFixedPointDecimal │ int64    │ 8    │ 1                   │
└────────┴──────────┴─────────────────────────┴──────────┴──────┴─────────────────────┘
```
All these value classes get inlined, and all these timestamp get/set operations boil down into couple of additions/subtractions.

The purpose of these niche value types and features is to demonstrate that you can easily do complicated things while not complicating your serialization layer 
AND making your code more readable (`record${tag}` methods and equivalent `getTime${tag}` methods are all generated and require no manual coding). 

We now have 5 very percise full timestamps only using 16 bytes. Each timestamp carries service specific tag (`77` in this case) and is effectively a unique id. 

You can very easily add a special IR type which recognises java DateTime and treats it as a single long in SBE layer, converting back to DateTime when decoding into object.

Note you cannot make accidental conversion mistakes between time units when using `TimeId` or `MicroTag4` because both of those use
`Nanos`, `Micros`, `Millis` value types, which will not compile unless you properly convert units when needed.

### Structs/Composite types

You can use structs via `isStruct` flag. Lets use this feature to define a `Signal` event published by some trading strategy, when its wants us to buy or sell.
Also we lets use some external buffer and demonstrate pinpointing some specific offset and read/writing from there.

```kotlin
typealias Time = TimeId
typealias Price = FPD

@SBE
data class Signal(
   var f1: Direction,
   var f2: XY,
)

enum class Direction { BUY, SELL }

@SBE(isStruct = true, usePadding = true)
data class XY(
   var time: Time,
   var price: Price,
)

fun main() {
   val buffer = UnsafeBuffer(ByteArray(1024))
   val flyweight = SignalRW(buffer, 500)

   flyweight
      .f1(Direction.BUY)
      .f2RW()
         .time(Time(System.currentTimeMillis()))
         .price(Price.fromExact("78000.0"))

   val signal = newSignal()

   flyweight.decodeInto(signal)
   // we can also directly decode we are not using the flyweight
   // it does the same thing but with extra steps and checks...
   // signal.decodeFrom(buffer, 500) 

   signal.printSbeLayout()
}
```
If we attempt to wrap a buffer which does not have enough capcity left to encode the message of that flyweights type we will get runtime error during such attempt, because our flyweight (and encode) method knows the actual size of the message in advance.

```text
────────────────────────────────────────────────────────────────────────────────────────┐
│ Signal (totalSize = 25 bytes)                                                          │
├────────┬──────────┬───────────┬───────────┬──────┬─────────────────────────────────────┤
│ Offset │ Field    │ Type      │ SBE Type  │ Size │ Value                               │
├────────┼──────────┼───────────┼───────────┼──────┼─────────────────────────────────────┤
│ 0      │ [HEADER] │ Header    │           │ 8    │ 17|1|1|0                            │
│ 8      │ f2       │ XY        │ XY        │ 16   │ XY(time=1770008258490, price=78000) │
│ 24     │ f1       │ Direction │ Direction │ 1    │ BUY                                 │
└────────┴──────────┴───────────┴───────────┴──────┴─────────────────────────────────────┘
```
If we check the schema we will see that KSP is using `sbe:composite`.

```xml
<enum name='Direction' encodingType='uint8'>
   <validValue name='BUY'>0</validValue>
   <validValue name='SELL'>1</validValue>
</enum>
<composite name='XY'>
   <type name='time' primitiveType='int64' offset='0'/>
   <type name='price' primitiveType='int64' offset='8'/>
</composite>
```
But it also defines flyweights for the composite. Which is accessible via special methods.
See the source code for `.f2RW()`, since we know we are positioned correctly 
this allows us to avoid any unnecessary `warp()` invocations when invoking `encoder.f2()`.

### Nesting other @SBE types

You can nest other messages.

```kotlin
@SBE
data class Signal(
   var direction: Direction,
   var xy: XY,
)

enum class Direction { BUY, SELL }

@SBE
data class XY(
   var time: Time,
   var price: Price,
)

fun main() {
   randomSignal().printSbeLayout()
   randomXY().printSbeLayout()
}
```
You can see that ByteArrayN type is used to "reserve" the space for the nested message.
This is not something supported by SBE, from its perspective its just a byteArray with fixed size, but we are operating a layer above.

```text
┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Signal (totalSize = 33 bytes)                                                                                  │
├────────┬───────────┬───────────┬─────────────┬──────┬──────────────────────────────────────────────────────────┤
│ Offset │ Field     │ Type      │ SBE Type    │ Size │ Value                                                    │
├────────┼───────────┼───────────┼─────────────┼──────┼──────────────────────────────────────────────────────────┤
│ 0      │ [HEADER]  │ Header    │             │ 8    │ 25|1|1|0                                                 │
│ 8      │ xy        │ XY        │ ByteArray24 │ 24   │ XY(time=-7194670948270344170, price=5749190915.69403576) │
│ 32     │ direction │ Direction │ Direction   │ 1    │ SELL                                                     │
└────────┴───────────┴───────────┴─────────────┴──────┴──────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────────────┐
│ XY (totalSize = 24 bytes)                                                            │
├────────┬──────────┬─────────────────────────┬──────────┬──────┬──────────────────────┤
│ Offset │ Field    │ Type                    │ SBE Type │ Size │ Value                │
├────────┼──────────┼─────────────────────────┼──────────┼──────┼──────────────────────┤
│ 0      │ [HEADER] │ Header                  │          │ 8    │ 16|2|1|0             │
│ 8      │ time     │ TimeId                  │ int64    │ 8    │ -4482107186970505818 │
│ 16     │ price    │ PackedFixedPointDecimal │ int64    │ 8    │ 192.738283729844301  │
└────────┴──────────┴─────────────────────────┴──────────┴──────┴──────────────────────┘
```

You should experiment with changing nested types into composites, using complex types in composites, using padding etc...
This will give you better idea what the code generation does.
I also recommend inspecting what the generated code actually does, 
sometimes there are more efficient ways to implment these.

### Using union types

This is one of the features that is quite frequently used in other systems. 
If you use SBE you most likely have a method that adds 4 bytes representing what the payload is then uses actual gneerated Decoders to extract the payload (and vice versa).
In protobuf this is known as `oneof`, when your wrapper type can have only one of the specified types as payload.

Here is an example of using union types.

```kotlin
@SBE
data class Wrapper(
   var id: TimeId,
   @META(oneOf = [Failure::class, Success::class])
   var result: Any,
)

@SBE(id = 2)
data class Failure(var id: TimeId, var code: Int)

@SBE(id = 1)
data class Success(var id: TimeId, var result: Long)
```
I'm still considering options but for now unions are stored as a composite.
Another option is to declare each type in SBE but give them all same offset where the byte buffer is now...

```xml
<composite name='resultUnion'>
  <type name='type' primitiveType='int32'/>
  <type name='data' primitiveType='uint8' length='24'/>
</composite>
```
The type is used as a discriminator and uses id of the message (TODO what to do about structs, those dont have an id).

```xml
<sbe:message name='Success' id='1' description='Success'>
  <field name='id' id='1' type='int64'/>
  <field name='result' id='2' type='int64'/>
</sbe:message>
<sbe:message name='Failure' id='2' description='Failure'>
   <field name='id' id='1' type='int64'/>
   <field name='code' id='2' type='int32'/>
</sbe:message>
<sbe:message name='Wrapper' id='3' description='Wrapper'>
   <field name='result' id='2' type='resultUnion'/>
   <field name='id' id='1' type='int64'/>
</sbe:message>
```

It is important that the ids are consistent, i recommend turning on the `requireMessageIds` for real usage. Adding new types will change order/id that is assigned. 

Generated code of those messages is then used to deserialize the `data` field.

```kotlin
fun main() {
   val msg = Wrapper(id = TimeId.now(), result = newSuccess(TimeId.now(), 42L))

   // unlike java kotlin has cutting edge features such as
   // not needing to cast to a type if you just checked its type...
   when (val payload = msg.result) {
      is Success -> println("success : ${payload.id} ${payload.result}") // hence payload is of type Success here
      is Failure -> println("failure : ${payload.id} ${payload.code}") // same goes for failure case
   }

   msg.printSbeLayout()
   randomSuccess().printSbeLayout()
}
```
`resultUnion` field will have 4 extra bytes (thats the discriminator).

```text
┌───────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Wrapper (totalSize = 44 bytes)                                                                            │
├────────┬──────────┬────────┬─────────────┬──────┬─────────────────────────────────────────────────────────┤
│ Offset │ Field    │ Type   │ SBE Type    │ Size │ Value                                                   │
├────────┼──────────┼────────┼─────────────┼──────┼─────────────────────────────────────────────────────────┤
│ 0      │ [HEADER] │ Header │             │ 8    │ 36|3|1|0                                                │
│ 8      │ result   │ Any    │ resultUnion │ 28   │ Success(id=Nanos(nanos=1770216009303000000), result=42) │
│ 36     │ id       │ TimeId │ int64       │ 8    │ Nanos(nanos=1770216009303000000)                        │
└────────┴──────────┴────────┴─────────────┴──────┴─────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────────┐
│ Success (totalSize = 24 bytes)                                                   │
├────────┬──────────┬────────┬──────────┬──────┬───────────────────────────────────┤
│ Offset │ Field    │ Type   │ SBE Type │ Size │ Value                             │
├────────┼──────────┼────────┼──────────┼──────┼───────────────────────────────────┤
│ 0      │ [HEADER] │ Header │          │ 8    │ 16|1|1|0                          │
│ 8      │ id       │ TimeId │ int64    │ 8    │ Nanos(nanos=-7435250699869327733) │
│ 16     │ result   │ Long   │ int64    │ 8    │ 6837095934708511301               │
└────────┴──────────┴────────┴──────────┴──────┴───────────────────────────────────┘
```

---
## Core Features (current state of the library)

1. [x] primitives support `Boolean`, `Char`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`
2. [x] support `usePadding` for optional alignment
3. [x] support `Enums`
4. [x] support `Value` classes
5. [x] support `typealias`
6. [x] support `flyweight` 3 variants (`R` read only , `W` write only, `RW` read/write)
7. [x] generate tests
8. [x] support fixed size `byte[]` via `@META(size=N)`
9. [x] support fixed size `String` via `@META(size=N)`
10. [x] add specialized value type `PackedFixedPointDecimal` packed `FPD` where both value and scale encoded
11. [x] add specialized value type `FixedPointDecimalN` specialized `FPDN` where scale is implied/hardcoded
12. [x] add specialized value type `TimeID` for distributed timestamps, fast unique id generation
13. [x] add support for custom charset strings `str64`
14. [x] support `composite` types via `@SBE(isStruct=true)`
15. [x] add support for nesting `@SBE` messages
16. [x] add support for unions
17. [x] add support for tagging timestamp diffs in message (telemetry within message)
18. [x] add support for autogenerated tests
19. [ ] add support for nullability (adds a bool isSet$field)
20. [ ] add support for `compactFlags` nullability + normal boolean fields stored via bitset `sbe:set`
21. [ ] support for fixed size `Array` via `@META(size=N)`
22. [ ] support for fixed size `Map` via `@META(size=N)`
23. [ ] support for fixed size `Set` via `@META(size=N)`
24. [ ] add support for some date time api objects
25. [ ] add support for var length type `String/CharSequence` (disables flyweights)
26. [ ] add support for var length type `List<T>` (disables flyweights)
27. [ ] add support for var length type `Map<T>` (disables flyweights)
28. [ ] add support for var length type `Set<T>` (disables flyweights)
29. [ ] add support for `javaFriendly` code generation
30. [ ] add support for `version`, `since`
31. [ ] add support for `generateTests` flag to control per message Test generation
32. [ ] add configuration option `requireMessageIds` (fails compilation if a message has no id)
33. [ ] add configuration option `requireNotNulls` (cannot use `allowNullableTypes`)
34. [ ] add configuration option `requireFixedLength` (cannot use `allowVarLengthFields`)
35. [ ] publish `sbe4kt` as artefact to mvn local and make it usable from other projects as a plugin

## Issues/Questions/Notes

1. [ ] codegen can override each other (name collisions/file collisions), same name enums...
2. [ ] dont like reinventing this encoding/decoding for str64 and strN value classes, maybe better solution out there?
3. [ ] try cyclical dependencies
4. [ ] using int8/int16 directly instead of sbe enum? is there a performance overhead when using sbe enums?
5. [ ] why is sbe using short when underlying is byte?
6. [ ] when not using String but CharSequence fw could keep instance of StringBuilder for getter invocations.
7. [ ] printSbeStructure() for structs? or print it when printed for a message.
8. [ ] maybe i should make str64 types just add `S64` instead of `Str64`
9. [ ] union support when one of types are structs is messy, needs revisit and proper solution.
10. [ ] make validation more strict since @SBE and @META tags have multiple purposes, user can create nonsensical combinations of arguments (fail fast)

## Features to experiment with

* Slab byte[] allocators for buffer reuse
  * when you need many messages around instead of processing one at a time

* Extensions over Aeron IPC/UDP for GRPC like bidirectional communication
  * one to one (Req - Rep)
  * one to many (Req - IObservable<Rep>)
  * many to one (IObservable<Req> - Rep) 
    * this can be universal way to deal with "what if i need really big string in my message"
    * send many small ones until its done
  * many to many (IObservable<Req> - IObservable<Rep>)

---
## Contributing

KSP api is very flexible, all of the generated code and schemas are there for you to observe
and verify that they do exactly what you want them to do.

Few things to keep in mind. Stay in line with the principles mentioned above. Many of these choices are deliberate
to discourage developers from misusing or building an unmaintainable mess.

Generated code has no warnings or comments, keep it that way if you are going to contribute,
make the code self documenting, the method/field/variable names should be self-explanatory,
using long names is ok, for very long names use `typealiases`. Keep the code compact so it is maintainable.

I recommend to always look at the generated sbe schema and class files first,
sometimes you may copy the old version and diff with new generations.

To debug the plugin while it is compiling your classes first run this in terminal (with correct jdk)

```bash
./gradlew clean run --no-daemon -Dorg.gradle.debug=true -Dkotlin.compiler.execution.strategy=in-process
```

this runs Gradle in a mode where it expects a debugger to attach, then run `ksp_debug_idea` to actually attach a debugger.
Now your breakpoints under `sbe4kt` module won't be ignored.

---
## Code Structure

Entry point is for the plugin is [Sbe4Kt.kt](sbe4kt/src/main/kotlin/sbe4kt/Sbe4Kt.kt),
It will detect all the classes annotated with [SBE.kt](sbe4kt/src/main/kotlin/sbe4kt/SBE.kt),  
build an Intermediate Represenation of the Schema defined at [definitions.kt](sbe4kt/src/main/kotlin/sbe4kt/ir/definitions.kt),
then generate SBE schema via [sbe-generator.kt](sbe4kt/src/main/kotlin/sbe4kt/sbe/sbe-generator.kt)
and kotlin extensions via [kt-generator.kt](sbe4kt/src/main/kotlin/sbe4kt/kt/kt-generator.kt).

Anything that is of type `IR.Root` is a message root which recursively resolves the intermediate fields and their `IR.Leaf` types.
Each `IR.Leaf` type contains generic information ONLY.

Context information such as order of the fields, their ids, padding etc is all stored in separate two types `Schema` and `Field` which have references to their respective `IR` types.

Do not break this architecture, avoid hardcoding any conditional logic in generators.

Schema instance should contain everything in normalized form for generators do their job.

IR types are sealed hence whenever you have some conditional logic use exhaustive when statements,
this way if new IR types are added later compiler will force the developer to address the missing logic.
