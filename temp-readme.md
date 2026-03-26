### Design of Flyweights in SBE4KT generated code

#### Single shared buffer reference, eagerly initialized

Every flyweight holds a `val buffer: UnsafeBuffer` — a single, final reference to the underlying buffer.
Nested flyweights (structs, unions, arrays) receive the **same** buffer instance at construction time
and store a pre-computed `offset` relative to the buffer's start.

```kotlin
class MsgFW(
   val buffer: UnsafeBuffer = UnsafeBuffer(ByteArray(MsgCodec.SIZE)),
   var offset: Int = 0,
) {
   private val payloadFW = PayloadFW(buffer, MsgCodec.offsetPayload(offset))
   // payloadFW.buffer === this.buffer (same reference)
}
```

Because the buffer reference is shared and final, **a single `buffer.wrap(...)` call repositions the
entire flyweight tree** — the parent and all nested children automatically read/write from the new
location without any additional setup.

```kotlin
val fw = MsgFW()
fw.buffer.wrap(ringBuffer, position, MsgCodec.SIZE)
// fw and fw.payload() now both point to the new position
```

#### Contrast with SBE Java Encoder/Decoder approach

SBE's generated Java codecs (`MsgEncoder`, `MsgDecoder`) use lazy wrapping — each nested encoder/decoder
maintains its own independent buffer reference and is wrapped on access:

```java
// SBE Java — each access creates or re-wraps a child encoder
public PayloadEncoder payload() {
    payload.wrap(buffer, offset + 16);  // wraps its own internal buffer reference
    return payload;
}
```

This means every field access that returns a nested type triggers a `wrap()` call, and repositioning
requires re-wrapping each level of the hierarchy individually.

SBE4KT flyweights avoid this entirely:

| | SBE Java Encoder/Decoder | SBE4KT Flyweight |
|---|---|---|
| Buffer ownership | Each level wraps independently | One shared `val buffer` reference |
| Nested access | Lazy — `wrap()` on every `.payload()` call | Eager — offset computed once at construction |
| Repositioning | Must re-wrap each level | Single `buffer.wrap()` propagates to all |
| Offset model | Internally managed, hidden | Public `var offset`, relative to buffer start |

#### Offset semantics

The `offset` field on each flyweight represents its absolute position within the buffer.
For a top-level flyweight this is always `0` (the buffer itself is positioned via `wrap()`).
For nested flyweights the offset is computed from the parent's offset plus the field's known
position within the message layout:

```
MsgFW          offset = 0
└─ PayloadFW   offset = MsgCodec.offsetPayload(0) = 16
```

All offsets are fixed at construction time because SBE messages have a fixed layout — field
positions never change. This means no runtime offset recalculation is needed when accessing fields.
