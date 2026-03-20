# Reusability Enforcement & Flyweight Fixed/Variable Split

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce the reusability principle at schema generation time (reject immutable types, require StringBuilder/ArrayList/Agrona primitive lists) and split flyweight generation into fixed-size (with getters/setters) vs variable-size (bulk-only) modes.

**Architecture:** IR leaf types are updated to represent reusable types (StringBuilder, ArrayList, IntArrayList, LongArrayList). Type resolution in `definitions.kt` rejects immutable alternatives with clear error messages. The flyweight generator conditionally emits per-field getters/setters only for fixed-size messages.

**Tech Stack:** Kotlin, KSP, SBE, Agrona

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/VarStringN.kt` | Modify | name→StringBuilder, assignToInstanceField uses clear().append() |
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/StringN.kt` | Modify | name→StringBuilder, assignToInstanceField uses clear().append() |
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/Array.kt` | Delete | Replaced by ArrayListGroup |
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/List.kt` | Modify | Rename class to ArrayListGroup, update for ArrayList semantics |
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/IntArrayListGroup.kt` | Create | IR type for org.agrona.collections.IntArrayList |
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/LongArrayListGroup.kt` | Create | IR type for org.agrona.collections.LongArrayList |
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/definitions.kt` | Modify | Reject String/Array/List, accept StringBuilder/ArrayList/IntArrayList/LongArrayList |
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/kt/kt-generator-message-flyweights.kt` | Modify | Fixed/variable split, reusable decode patterns |
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/kt/kt-generator-structs.kt` | Modify | Use assignToInstanceField in struct decodeInto |
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/kt/kt-generator-message-tests.kt` | Modify | Variable-size tests use ExpandableArrayBuffer, no RW |
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/sbe/sbe-generator.kt` | Modify | Handle new IR type names (ArrayListGroup replaces Array/List) |
| `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/Struct.kt` | Modify | Remove dead `${name}RW` import reference |
| `src/main/kotlin/example/example.kt` | Modify | Use valid reusable types |

---

## Chunk 1: IR Type Changes

### Task 1: Update VarStringN for StringBuilder

**Files:**
- Modify: `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/VarStringN.kt`

- [ ] **Step 1: Update VarStringN**

Change `name` to `StringBuilder`, update `defaultValue`/`randomValue`, override `assignToInstanceField` for clear().append() reuse pattern:

```kotlin
package sbe4kt.ir

import sbe4kt.META.Encoding

internal data class VarStringN(val encoding: Encoding) : IR.Leaf {
   override val sbeName = if (encoding == Encoding.UTF8) "varStrU" else "varStr"
   override val name = "StringBuilder"
   override val defaultValue = "StringBuilder()"
   override val randomValue = "StringBuilder(Random.nextStr(8))"
   override val size = -1
   override val packageStr = ""
   override val imports = setOf("sbe4kt.lib.nextStr")

   override fun fwSetter(encoder: String, field: String) = "$encoder.$field($field)"
   override fun fwGetter(decoder: String, field: String) = "$decoder.$field()"

   override fun assignToInstanceField(instance: String, field: String, value: String) =
      "$instance.$field.clear().append($value)"
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd sbe4kt-ksp && ../gradlew compileKotlin`
Expected: compiles (no downstream consumers changed yet)

- [ ] **Step 3: Commit**

```bash
git add sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/VarStringN.kt
git commit -m "ir: VarStringN name→StringBuilder with reuse pattern"
```

---

### Task 2: Update StringN for StringBuilder

**Files:**
- Modify: `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/StringN.kt`

- [ ] **Step 1: Update StringN**

Change `name` to `StringBuilder`, update `defaultValue`/`randomValue`, override `assignToInstanceField`:

```kotlin
package sbe4kt.ir

import sbe4kt.META.Encoding

internal data class StringN(val length: Int, val encoding: Encoding = Encoding.ASCII) : IR.Leaf {
   override val sbeName = "CharArray$length${if (encoding == Encoding.UTF8) "U" else ""}"
   override val name = "StringBuilder"
   override val defaultValue = "StringBuilder()"
   override val randomValue = "StringBuilder(Random.nextStr($length))"
   override val size = length
   override val packageStr = ""
   override val imports: Set<String> = setOf(
      "sbe4kt.lib.nextStr"
   )

   override fun encodeField(encoder: String, field: String, value: String) = "$encoder.put$field($value)"
   override fun decodeField(decoder: String, field: String) = "$decoder.get$field()"

   override fun fwSetter(encoder: String, field: String) = "$encoder.${field}($field)"
   override fun fwGetter(decoder: String, field: String) = "$decoder.${field}()"

   override fun assignToInstanceField(instance: String, field: String, value: String) =
      "$instance.$field.clear().append($value)"
}
```

- [ ] **Step 2: Verify and commit**

```bash
cd sbe4kt-ksp && ../gradlew compileKotlin
git add sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/StringN.kt
git commit -m "ir: StringN name→StringBuilder with reuse pattern"
```

---

### Task 3: Replace Array/List IR types with ArrayListGroup

**Files:**
- Delete: `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/Array.kt`
- Modify: `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/List.kt` → rename class to `ArrayListGroup`

- [ ] **Step 1: Delete Array.kt**

```bash
git rm sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/Array.kt
```

- [ ] **Step 2: Rewrite List.kt as ArrayListGroup**

The `name` becomes `ArrayList<T>`, `defaultValue` uses `ArrayList()`, `randomValue` populates an ArrayList. Override `assignToInstanceField` to NOT assign (groups populate in-place via decodeInto body).

```kotlin
package sbe4kt.ir

internal sealed interface RepeatingGroup {
   val elementType: IR.Leaf
}

internal data class ArrayListGroup(override val elementType: IR.Leaf) : IR.Leaf, RepeatingGroup {
   override val sbeName = elementType.sbeName
   override val name = "ArrayList<${elementType.name}>"
   override val size = -1
   override val defaultValue = "ArrayList()"
   override val randomValue = when (elementType) {
      is Struct -> "arrayListOf(${elementType.randomValue}, ${elementType.randomValue})"
      else -> "arrayListOf(${elementType.randomValue}, ${elementType.randomValue})"
   }
   override val packageStr = ""
   override val imports get() = elementType.imports
}
```

Note: `randomValue` creates a small fixed-size list for simplicity. The `arrayListOf()` function returns `ArrayList`.

- [ ] **Step 3: Fix all compile errors from Array/List removal**

Update imports in all files that reference `sbe4kt.ir.Array` or `sbe4kt.ir.List`:
- `definitions.kt` — change `Array(...)` and `List(...)` constructors to `ArrayListGroup(...)`
- `kt-generator-message-flyweights.kt` — change `is Array` / `is List` checks to `is ArrayListGroup`
- `sbe-generator.kt` — no changes needed (uses `RepeatingGroup` interface)
- `kt-generator-message-utils.kt` — no changes needed (uses `RepeatingGroup` interface)
- `Message.kt` — no changes needed (uses `RepeatingGroup` interface)

Also clean up dead RW reference in `Struct.kt` — remove line 65:
```kotlin
add("${pkg}.${name}RW")  // DELETE THIS LINE
```
The RW combined flyweight no longer exists.

Run: `cd sbe4kt-ksp && ../gradlew compileKotlin`
Expected: compiles

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "ir: replace Array/List with ArrayListGroup for reusability"
```

---

### Task 4: Add IntArrayListGroup and LongArrayListGroup IR types

**Files:**
- Create: `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/IntArrayListGroup.kt`
- Create: `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/LongArrayListGroup.kt`

- [ ] **Step 1: Create IntArrayListGroup**

```kotlin
package sbe4kt.ir

internal data object IntArrayListGroup : IR.Leaf, RepeatingGroup {
   override val elementType: IR.Leaf = Int32
   override val sbeName = "int32"
   override val name = "IntArrayList"
   override val size = -1
   override val defaultValue = "IntArrayList()"
   override val randomValue = "IntArrayList().apply { repeat(Random.nextInt(1, 5)) { addInt(Random.nextInt()) } }"
   override val packageStr = ""
   override val imports = setOf(
      "kotlin.random.Random",
      "org.agrona.collections.IntArrayList"
   )
}
```

- [ ] **Step 2: Create LongArrayListGroup**

```kotlin
package sbe4kt.ir

internal data object LongArrayListGroup : IR.Leaf, RepeatingGroup {
   override val elementType: IR.Leaf = Int64
   override val sbeName = "int64"
   override val name = "LongArrayList"
   override val size = -1
   override val defaultValue = "LongArrayList()"
   override val randomValue = "LongArrayList().apply { repeat(Random.nextInt(1, 5)) { addLong(Random.nextLong()) } }"
   override val packageStr = ""
   override val imports = setOf(
      "kotlin.random.Random",
      "org.agrona.collections.LongArrayList"
   )
}
```

- [ ] **Step 3: Verify and commit**

```bash
cd sbe4kt-ksp && ../gradlew compileKotlin
git add sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/IntArrayListGroup.kt sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/LongArrayListGroup.kt
git commit -m "ir: add IntArrayListGroup and LongArrayListGroup for Agrona primitives"
```

---

## Chunk 2: Type Resolution Enforcement

### Task 5: Update definitions.kt to reject immutable types and accept reusable ones

**Files:**
- Modify: `sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/definitions.kt`

- [ ] **Step 1: Reject String, Array, List with clear errors**

In `KSPropertyDeclaration.toLeaf()`, add rejections BEFORE the current resolution logic. Remove existing `kotlin.String`/`kotlin.CharSequence` → VarStringN resolution. Remove `kotlin.Array` and `kotlin.collections.List` detection.

Add at the top of `toLeaf()` (after `oneOf` and sealed class checks, before `size > 0` check):

```kotlin
// Reusability violations — reject immutable types with clear messages
if (declaration.qualifiedName() == "kotlin.String" || declaration.qualifiedName() == "kotlin.CharSequence") {
   throw IllegalArgumentException(
      "Field '${simpleName.asString()}': String is not reusable. Use StringBuilder instead. " +
      "For fixed-size: @META(size=N) var field: StringBuilder. " +
      "For variable-length: var field: StringBuilder"
   )
}

if (declaration.qualifiedName() == "kotlin.Array") {
   throw IllegalArgumentException(
      "Field '${simpleName.asString()}': Array<T> is not reusable. Use ArrayList<T> for objects, " +
      "IntArrayList/LongArrayList for primitives."
   )
}

if (declaration.qualifiedName() == "kotlin.collections.List" ||
    declaration.qualifiedName() == "kotlin.collections.MutableList") {
   throw IllegalArgumentException(
      "Field '${simpleName.asString()}': List<T> is not reusable. Use ArrayList<T> for objects, " +
      "IntArrayList/LongArrayList for primitives."
   )
}
```

- [ ] **Step 2: Add StringBuilder detection**

Replace the old String/CharSequence handling. After the `size > 0` block, add StringBuilder detection:

```kotlin
// StringBuilder — reusable string type
if (declaration.qualifiedName() == "kotlin.text.StringBuilder" ||
    declaration.qualifiedName() == "java.lang.StringBuilder") {
   val encoding = meta.encoding()
   return if (size > 0) StringN(size, encoding) else VarStringN(encoding)
}
```

Also update the `size > 0` block to handle StringBuilder:
```kotlin
if (size > 0) {
   if (declaration.qualifiedName() == "kotlin.ByteArray") {
      require(size > 0) { "ByteArray size must be positive, got $size" }
      return ByteArrayN(size)
   }
   if (declaration.qualifiedName() == "kotlin.text.StringBuilder" ||
       declaration.qualifiedName() == "java.lang.StringBuilder") {
      require(size > 0) { "StringBuilder size must be positive, got $size" }
      val encoding = meta.encoding()
      return StringN(size, encoding)
   }
}
```

- [ ] **Step 3: Add ArrayList detection with boxing rejection**

After StringBuilder detection, add:

```kotlin
// ArrayList<T> — reusable collection for object types
if (declaration.qualifiedName() == "java.util.ArrayList") {
   val typeArg = resolvedType.arguments.first().type!!.resolve().declaration
   val qualName = typeArg.qualifiedName()

   // Reject boxed primitives — use IntArrayList/LongArrayList instead
   val boxedPrimitives = setOf("kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double",
      "kotlin.Short", "kotlin.Byte", "kotlin.Boolean", "kotlin.Char",
      "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double",
      "java.lang.Short", "java.lang.Byte", "java.lang.Boolean", "java.lang.Character")
   if (qualName in boxedPrimitives) {
      val hint = when (qualName) {
         "kotlin.Int", "java.lang.Integer" -> " Use IntArrayList instead."
         "kotlin.Long", "java.lang.Long" -> " Use LongArrayList instead."
         else -> " Primitive type '$qualName' is not supported in repeating groups."
      }
      throw IllegalArgumentException(
         "Field '${simpleName.asString()}': ArrayList<${typeArg.simpleName()}> causes boxing.$hint"
      )
   }

   val elementType = resolveGroupElementType(typeArg, meta)
   return ArrayListGroup(elementType)
}
```

- [ ] **Step 4: Add IntArrayList and LongArrayList detection**

```kotlin
// Agrona primitive collections — reusable, unboxed
if (declaration.qualifiedName() == "org.agrona.collections.IntArrayList") {
   return IntArrayListGroup
}

if (declaration.qualifiedName() == "org.agrona.collections.LongArrayList") {
   return LongArrayListGroup
}
```

- [ ] **Step 5: Remove old String/Array/List handling**

Remove these blocks that are now dead code:
- The `kotlin.Array` detection block (lines ~212-216)
- The `kotlin.collections.List` detection block (lines ~219-223)
- The `kotlin.String` / `kotlin.CharSequence` → VarStringN block (lines ~226-229)

- [ ] **Step 6: Verify KSP module compiles**

Run: `cd sbe4kt-ksp && ../gradlew compileKotlin`
Expected: compiles (example will fail later since it uses Array<Float>)

- [ ] **Step 7: Commit**

```bash
git add sbe4kt-ksp/src/main/kotlin/sbe4kt/ir/definitions.kt
git commit -m "enforce reusability: reject String/Array/List, accept StringBuilder/ArrayList/IntArrayList/LongArrayList"
```

---

## Chunk 3: Flyweight Generator Changes

### Task 6: Split flyweight generation by fixed vs variable size

**Files:**
- Modify: `sbe4kt-ksp/src/main/kotlin/sbe4kt/kt/kt-generator-message-flyweights.kt`

The key change: `generateReaderFlyweight` and `generateWriterFlyweight` check `message.hasVariableFields` and conditionally include/exclude per-field getters/setters.

- [ ] **Step 1: Update generateReaderFlyweight**

In the reader flyweight:
- If `message.hasVariableFields`: omit `simpleFieldGetters`, `compositeFwAccessors`, `groupFieldGetters`, `unionGetters`. Keep only `decodeInto` and `size()`.
- `size()` returns the size constant (which is already -1 for variable messages).
- Remove the `constructor(encoder, decoder)` overload (it takes an encoder parameter that the reader doesn't use).

For the `decodeIntoBody`: use `assignToInstanceField` for simple fields instead of direct assignment:

Change:
```kotlin
else -> "target.${it.name} = ${it.name}()"
```
To:
```kotlin
else -> it.type.assignToInstanceField("target", it.name, it.type.fwGetter("decoder", it.name))
```

For RepeatingGroup fields in decodeInto, generate in-place population:
```kotlin
is RepeatingGroup -> {
   val elemType = (it.type as RepeatingGroup).elementType
   when (it.type) {
      is IntArrayListGroup -> buildString {
         appendLine("run {")
         appendLine("   target.${it.name}.clear()")
         appendLine("   for (grpDec in decoder.${it.name}()) {")
         appendLine("      target.${it.name}.addInt(grpDec.value())")
         appendLine("   }")
         append("}")
      }
      is LongArrayListGroup -> buildString {
         appendLine("run {")
         appendLine("   target.${it.name}.clear()")
         appendLine("   for (grpDec in decoder.${it.name}()) {")
         appendLine("      target.${it.name}.addLong(grpDec.value())")
         appendLine("   }")
         append("}")
      }
      is ArrayListGroup -> {
         val valueExpr = when (elemType) {
            is Struct -> {
               val fieldDecodes = elemType.fields.sortedBy { f -> f.sbeOrder }.joinToString(", ") { f ->
                  "${f.name} = ${f.type.convertFromSbe("grpDec.${f.name}()")}"
               }
               "${elemType.name}($fieldDecodes)"
            }
            else -> elemType.convertFromSbe("grpDec.value()")
         }
         buildString {
            appendLine("run {")
            appendLine("   target.${it.name}.clear()")
            appendLine("   for (grpDec in decoder.${it.name}()) {")
            appendLine("      target.${it.name}.add($valueExpr)")
            appendLine("   }")
            append("}")
         }
      }
      else -> throw IllegalArgumentException("Unexpected RepeatingGroup subtype: ${it.type::class}")
   }
}
```

- [ ] **Step 2: Update generateWriterFlyweight**

Mirror the reader changes:
- If `message.hasVariableFields`: omit `simpleFieldSetters`, `compositeFwAccessors`, `groupFieldSetters`, `unionSetters`.
- Remove the `constructor(encoder, decoder)` overload.

For RepeatingGroup fields in encodeFrom, handle new types:
```kotlin
is RepeatingGroup -> {
   val elemType = (it.type as RepeatingGroup).elementType
   when (it.type) {
      is IntArrayListGroup -> buildString {
         appendLine("run {")
         appendLine("   val grp = encoder.${it.name}Count(source.${it.name}.size())")
         appendLine("   for (i in 0 until source.${it.name}.size()) {")
         appendLine("      grp.next()")
         appendLine("      grp.value(source.${it.name}.getInt(i))")
         appendLine("   }")
         append("}")
      }
      is LongArrayListGroup -> buildString {
         appendLine("run {")
         appendLine("   val grp = encoder.${it.name}Count(source.${it.name}.size())")
         appendLine("   for (i in 0 until source.${it.name}.size()) {")
         appendLine("      grp.next()")
         appendLine("      grp.value(source.${it.name}.getLong(i))")
         appendLine("   }")
         append("}")
      }
      is ArrayListGroup -> {
         // existing pattern but using ArrayList iteration
         buildString {
            appendLine("run {")
            appendLine("   val grp = encoder.${it.name}Count(source.${it.name}.size)")
            appendLine("   for (item in source.${it.name}) {")
            appendLine("      grp.next()")
            when (elemType) {
               is Struct -> {
                  for (f in elemType.fields.sortedBy { it.sbeOrder }) {
                     appendLine("      grp.${f.name}(${f.type.convertToSbe("item.${f.name}")})")
                  }
               }
               else -> appendLine("      grp.value(${elemType.convertToSbe("item")})")
            }
            appendLine("   }")
            append("}")
         }
      }
      else -> throw IllegalArgumentException("Unexpected RepeatingGroup subtype")
   }
}
```

- [ ] **Step 3: Remove generateGroupFwGetters and generateGroupFwSetters**

These functions generated per-field group accessor methods. Since groups make messages variable-size, and variable-size messages have NO per-field accessors, these functions are no longer needed. All group encode/decode is inlined into encodeFrom/decodeInto.

Delete `generateGroupFwGetters` and `generateGroupFwSetters` functions. Update the flyweight generators to not call them.

- [ ] **Step 4: Verify KSP module compiles**

Run: `cd sbe4kt-ksp && ../gradlew compileKotlin`

- [ ] **Step 5: Commit**

```bash
git add sbe4kt-ksp/src/main/kotlin/sbe4kt/kt/kt-generator-message-flyweights.kt
git commit -m "flyweight: split fixed/variable, inline group encode/decode, use assignToInstanceField"
```

---

### Task 7: Update struct flyweight decode to use assignToInstanceField

**Files:**
- Modify: `sbe4kt-ksp/src/main/kotlin/sbe4kt/kt/kt-generator-structs.kt`

- [ ] **Step 1: Update generateStructReader decodeBody**

In `generateStructReader`, change the `decodeBody` generation from:
```kotlin
val decodeBody = struct.fields.joinToString("\n") {
   "target.${it.name} = ${it.type.convertFromSbe("decoder.${it.name}()")}"
}
```
To:
```kotlin
val decodeBody = struct.fields.joinToString("\n") {
   it.type.assignToInstanceField("target", it.name, it.type.convertFromSbe("decoder.${it.name}()"))
}
```

This ensures StringBuilder fields in structs use `clear().append()` instead of assignment.

- [ ] **Step 2: Verify and commit**

```bash
cd sbe4kt-ksp && ../gradlew compileKotlin
git add sbe4kt-ksp/src/main/kotlin/sbe4kt/kt/kt-generator-structs.kt
git commit -m "struct flyweight: use assignToInstanceField for reusable decode"
```

---

### Task 8: Update generated test template

**Files:**
- Modify: `sbe4kt-ksp/src/main/kotlin/sbe4kt/kt/kt-generator-message-tests.kt`

- [ ] **Step 1: Fix test template for variable-size messages**

The current test template always uses `UnsafeBuffer(ByteArray(SBE_SIZE))` and references `${className}RW()`. Fix:
- Variable-size messages: use `ExpandableArrayBuffer()` instead of fixed buffer
- Remove the `testFlyweights` test that uses RW (RW doesn't exist as a generated class)
- Or conditionally generate the flyweight test only for fixed-size messages

```kotlin
internal fun generateTestsSection(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()
   val bufferExpr = if (message.hasVariableFields) "ExpandableArrayBuffer()" else "UnsafeBuffer(ByteArray($sizeConstName))"

   return """
      |internal class ${className}Test {
      |
      |   @Test
      |   fun testRandomValues() {
      |      val buffer = $bufferExpr
      |      repeat(100) {
      |         val original = random$className()
      |         val bytesWritten = original.encodeTo(buffer)
      |
      |         val decoded = new$className()
      |         val bytesRead = decoded.decodeFrom(buffer)
      |
      |         assertTrue(bytesWritten > 0)
      |         assertEquals(bytesWritten, bytesRead)
      |         assertEquals(original, decoded)
      |      }
      |   }
      |}
      |""".trimTemplate()
}
```

- [ ] **Step 2: Verify and commit**

```bash
cd sbe4kt-ksp && ../gradlew compileKotlin
git add sbe4kt-ksp/src/main/kotlin/sbe4kt/kt/kt-generator-message-tests.kt
git commit -m "tests: fix generated test for variable-size messages, remove RW reference"
```

---

### Task 9: Update SBE schema generator for new IR types

**Files:**
- Modify: `sbe4kt-ksp/src/main/kotlin/sbe4kt/sbe/sbe-generator.kt`

- [ ] **Step 1: Update imports and type checks**

Replace `import sbe4kt.ir.Array` (if present) with new imports. The schema generator mostly works through the `RepeatingGroup` interface, so changes should be minimal. Verify that `IntArrayListGroup`/`LongArrayListGroup` work with existing group XML generation logic.

Check `extractSpecialTypes` in `definitions.kt` (Schema class) — ensure it handles the new group types. Currently it does:
```kotlin
is RepeatingGroup -> extractSpecialTypes(type.elementType)
```
This works for all RepeatingGroup implementations since `elementType` is always defined.

- [ ] **Step 2: Verify and commit**

```bash
cd sbe4kt-ksp && ../gradlew compileKotlin
git add sbe4kt-ksp/src/main/kotlin/sbe4kt/sbe/sbe-generator.kt
git commit -m "sbe-generator: update for new IR group types"
```

---

## Chunk 4: Example Update & Verification

### Task 10: Update example and verify end-to-end

**Files:**
- Modify: `src/main/kotlin/example/example.kt`

- [ ] **Step 1: Update example to use reusable types**

```kotlin
package example

import sbe4kt.SBE
import org.agrona.collections.IntArrayList

@SBE
data class Trade(
   var id: Long,
   var prices: IntArrayList,
)

@SBE(isStruct = true)
data class Payload(
   var f1: Int,
   var f2: Int,
)

enum class En {
   Val1, Val2
}

fun main() {
   val source = randomTrade()

   println(source)
   val buffer = org.agrona.ExpandableArrayBuffer()
   val bytesWritten = source.encodeTo(buffer)
   println("bytesWritten : $bytesWritten")

   val target = newTrade()
   val bytesRead = target.decodeFrom(buffer)
   println("bytesRead : $bytesRead")

   println(target)
   target.printSbeLayout(flat = true)
}
```

- [ ] **Step 2: Full clean build**

Run: `./gradlew clean compileKotlin`
Expected: compiles without errors

- [ ] **Step 3: Run example**

Run: `./gradlew :run`
Expected: encode/decode roundtrip succeeds, layout prints

- [ ] **Step 4: Inspect generated output**

Read `build/generated/ksp/main/kotlin/example/TradeExtensions.kt` and verify:
- `TradeR` has NO per-field getters (variable-size due to IntArrayList group)
- `TradeW` has NO per-field setters
- `size()` returns -1 (or SBE_SIZE_TRADE which equals -1)
- `decodeInto` uses `target.prices.clear()` + `addInt()` pattern
- `encodeFrom` uses `getInt()` pattern

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/example/example.kt
git commit -m "example: use IntArrayList for reusability compliance"
```

---

### Task 11: Fix existing tests

**Files:**
- Modify: tests under `src/test/kotlin/test/` that use String, Array, or List types

- [ ] **Step 1: Audit test files for reusability violations**

Check each test file for `@SBE` data classes that use:
- `String` → change to `StringBuilder`
- `Array<T>` or `List<T>` → change to `ArrayList<T>`, `IntArrayList`, or `LongArrayList`

Key files to check:
- `_7_strN.kt` — uses `String` with `@META(size=20)` → change to `StringBuilder`
- `_12_composite.kt` — may reference struct types
- Other test files with only primitive fields should be fine

- [ ] **Step 2: Run full test suite**

Run: `./gradlew clean test`
Expected: all tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/test/
git commit -m "tests: update to use reusable types (StringBuilder, ArrayList)"
```

---

### Task 12: Update MEMORY.md

**Files:**
- Modify: `/Users/vachagan.balayan/.claude/projects/-Users-vachagan-balayan-work--other-experimental-sbe4kt2/memory/MEMORY.md`

- [ ] **Step 1: Update memory with new rules**

Update the Reusability Principle and Bulk Read/Write sections to reflect:
- StringBuilder requirement (not String)
- ArrayList requirement (not List/Array)
- IntArrayList/LongArrayList for primitives
- Fixed vs variable flyweight distinction
- No getters/setters for variable-size messages

---

## Key Design Decisions

1. **StringBuilder in getters (fixed-size messages):** Getters return `String` (what SBE decoder gives). Setters accept `StringBuilder` (parameter type matches data class field). The pojo's StringBuilder is populated via `clear().append()` only through `decodeInto`.

2. **ArrayList.size vs IntArrayList.size():** `ArrayList.size` is a property, `IntArrayList.size()` is a method. The encode patterns must use the correct access form.

3. **No Float/Double/Short/Byte in repeating groups:** These primitives are unsupported since Agrona has no specialized list types for them. The type resolver will fall through to "Unsupported type" error if someone tries ArrayList<Float>.

4. **Struct flyweights unchanged:** Struct R/W classes remain as-is per user direction. Only the decode body uses `assignToInstanceField` for StringBuilder field support.

5. **RW class removed:** The combined RW flyweight pattern referenced in test templates is removed. Fixed-size messages get separate R and W with getters/setters. The generated test template no longer references RW.
