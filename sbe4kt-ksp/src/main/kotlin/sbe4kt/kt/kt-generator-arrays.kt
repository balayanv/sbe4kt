package sbe4kt.kt

import com.google.devtools.ksp.processing.Dependencies
import sbe4kt.KSP
import sbe4kt.ir.*
import sbe4kt.ir.Char
import sbe4kt.ir.Double
import sbe4kt.ir.Float

internal fun KSP.generateArraySupportFile(packageName: String, elementTypes: Set<IR.Leaf>) {
   val sections = mutableListOf<String>()

   for (elementType in elementTypes.sortedBy { it.name }) {
      sections.add(generateArrayCodec(elementType))
      sections.add(generateArrayFW(elementType))
   }

   // ArrayFW<T> for struct arrays if any struct array is used
   val hasStructArrays = ir.arraySizes.any { (elem, _) -> elem is Struct }
   if (hasStructArrays) {
      sections.add(generateGenericArrayFW())
   }

   if (sections.isEmpty()) return

   val file = gen.createNewFile(Dependencies(true), packageName, "ArraySupport")
   val content = """
      |package $packageName
      |
      |import org.agrona.DirectBuffer
      |import org.agrona.MutableDirectBuffer
      |import org.agrona.concurrent.UnsafeBuffer
      |import sbe4kt.lib.branchlessMin
      |
      |${sections.joinToString("\n\n")}
      |""".trimMargin()

   file.write(content.toByteArray())
   file.close()
}

private fun generateArrayCodec(elementType: IR.Leaf): String {
   val typeName = elementType.name  // "Byte", "Short", "Int", "Long", "Float", "Double", "Char"
   val codecName = "${typeName}ArrayCodec"
   val elemSize = elementType.size
   val indexExpr = if (elemSize == 1) "index" else "index * $elemSize"

   return when (elementType) {
      is Int8 -> """
         |object $codecName {
         |   const val BYTE_SIZE = $elemSize
         |
         |   fun get(buffer: DirectBuffer, offset: Int, index: Int): $typeName = buffer.getByte(offset + $indexExpr)
         |   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: $typeName) = apply { buffer.putByte(offset + $indexExpr, value) }
         |
         |   fun encodeFrom(source: ${typeName}Array, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
         |      buffer.putBytes(offset, source, from, length)
         |   }
         |
         |   fun decodeInto(target: ${typeName}Array, from: Int, length: Int, buffer: DirectBuffer, offset: Int) {
         |      buffer.getBytes(offset, target, from, length)
         |   }
         |
         |   fun size(count: Int): Int = count * BYTE_SIZE
         |}
      """.trimMargin()

      is Char -> """
         |object $codecName {
         |   const val CHAR_SIZE = $elemSize
         |
         |   fun get(buffer: DirectBuffer, offset: Int, index: Int): kotlin.Char = buffer.getByte(offset + $indexExpr).toInt().toChar()
         |   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: kotlin.Char) = apply { buffer.putByte(offset + $indexExpr, value.code.toByte()) }
         |   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: Byte) = apply { buffer.putByte(offset + $indexExpr, value) }
         |
         |   fun encodeFrom(source: ${typeName}Array, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
         |      for (i in 0 until length) set(buffer, offset, i, source[from + i])
         |   }
         |
         |   fun decodeInto(target: ${typeName}Array, from: Int, length: Int, buffer: DirectBuffer, offset: Int) {
         |      for (i in 0 until length) target[from + i] = get(buffer, offset, i)
         |   }
         |
         |   fun encodeFromCharSequence(source: CharSequence, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
         |      for (i in 0 until length) set(buffer, offset, i, source[from + i])
         |   }
         |
         |   fun decodeIntoAppendable(target: Appendable, length: Int, buffer: DirectBuffer, offset: Int) {
         |      for (i in 0 until length) {
         |         val b = get(buffer, offset, i)
         |         if (b == '\u0000') break
         |         target.append(b)
         |      }
         |   }
         |
         |   fun size(count: Int): Int = count * CHAR_SIZE
         |}
      """.trimMargin()

      else -> {
         val sizeConstName = "${typeName.uppercase()}_SIZE"
         val bufferGet = "buffer.get$typeName(offset + $indexExpr)"
         val bufferPut = "buffer.put$typeName(offset + $indexExpr, value)"

         """
         |object $codecName {
         |   const val $sizeConstName = $elemSize
         |
         |   fun get(buffer: DirectBuffer, offset: Int, index: Int): $typeName = $bufferGet
         |   fun set(buffer: MutableDirectBuffer, offset: Int, index: Int, value: $typeName) = apply { $bufferPut }
         |
         |   fun encodeFrom(source: ${typeName}Array, from: Int, length: Int, buffer: MutableDirectBuffer, offset: Int) {
         |      for (i in 0 until length) set(buffer, offset, i, source[from + i])
         |   }
         |
         |   fun decodeInto(target: ${typeName}Array, from: Int, length: Int, buffer: DirectBuffer, offset: Int) {
         |      for (i in 0 until length) target[from + i] = get(buffer, offset, i)
         |   }
         |
         |   fun size(count: Int): Int = count * $sizeConstName
         |}
         """.trimMargin()
      }
   }
}

private fun generateArrayFW(elementType: IR.Leaf): String {
   val typeName = elementType.name
   val fwName = "${typeName}ArrayFW"
   val codecName = "${typeName}ArrayCodec"

   return when (elementType) {
      is Char -> """
         |class $fwName(
         |   val buffer: UnsafeBuffer,
         |   val size: Int,
         |   val offset: Int = 0,
         |) : CharSequence {
         |   override fun get(index: Int): kotlin.Char = $codecName.get(buffer, offset, index)
         |   fun set(index: Int, value: kotlin.Char) = apply { $codecName.set(buffer, offset, index, value) }
         |   fun set(index: Int, value: Byte) = apply { $codecName.set(buffer, offset, index, value) }
         |
         |   fun encodeFrom(source: ${typeName}Array) = apply { $codecName.encodeFrom(source, 0, branchlessMin(source.size, size), buffer, offset) }
         |   fun encodeFrom(source: ${typeName}Array, from: Int, to: Int) = apply {
         |      val len = branchlessMin(to - from, size)
         |      $codecName.encodeFrom(source, from, len, buffer, offset)
         |      if (len < size) buffer.putByte(offset + len, 0)
         |   }
         |
         |   fun decodeInto(target: ${typeName}Array) = apply { $codecName.decodeInto(target, 0, branchlessMin(target.size, size), buffer, offset) }
         |   fun decodeInto(target: ${typeName}Array, from: Int, to: Int) = apply { $codecName.decodeInto(target, from, branchlessMin(to - from, size), buffer, offset) }
         |
         |   fun encodeFromCharSequence(source: CharSequence) = apply {
         |      val len = branchlessMin(source.length, size)
         |      $codecName.encodeFromCharSequence(source, 0, len, buffer, offset)
         |      if (len < size) buffer.putByte(offset + len, 0)
         |   }
         |   fun encodeFromCharSequence(source: CharSequence, from: Int, to: Int) = apply {
         |      val len = branchlessMin(to - from, size)
         |      $codecName.encodeFromCharSequence(source, from, len, buffer, offset)
         |      if (len < size) buffer.putByte(offset + len, 0)
         |   }
         |   fun decodeIntoAppendable(target: Appendable) = apply {
         |      $codecName.decodeIntoAppendable(target, size, buffer, offset)
         |   }
         |
         |   override val length: Int get() = size
         |   override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = StringBuilder(endIndex - startIndex).apply { for (i in startIndex until endIndex) append(get(i)) }
         |   override fun toString(): String = StringBuilder(size).also { $codecName.decodeIntoAppendable(it, size, buffer, offset) }.toString()
         |}
      """.trimMargin()

      else -> """
         |class $fwName(
         |   val buffer: UnsafeBuffer,
         |   val size: Int,
         |   val offset: Int = 0,
         |) {
         |   fun get(index: Int): $typeName = $codecName.get(buffer, offset, index)
         |   fun set(index: Int, value: $typeName) = apply { $codecName.set(buffer, offset, index, value) }
         |
         |   fun encodeFrom(source: ${typeName}Array) = apply { $codecName.encodeFrom(source, 0, branchlessMin(source.size, size), buffer, offset) }
         |   fun encodeFrom(source: ${typeName}Array, from: Int, to: Int) = apply { $codecName.encodeFrom(source, from, branchlessMin(to - from, size), buffer, offset) }
         |   fun decodeInto(target: ${typeName}Array) = apply { $codecName.decodeInto(target, 0, branchlessMin(target.size, size), buffer, offset) }
         |   fun decodeInto(target: ${typeName}Array, from: Int, to: Int) = apply { $codecName.decodeInto(target, from, branchlessMin(to - from, size), buffer, offset) }
         |}
      """.trimMargin()
   }
}

private fun generateGenericArrayFW(): String = """
   |class ArrayFW<T>(
   |   private val count: Int,
   |   private val stride: Int,
   |   private val wrapElement: (DirectBuffer, Int, Int) -> T,
   |   val buffer: UnsafeBuffer,
   |   val offset: Int = 0,
   |) {
   |   operator fun get(index: Int): T {
   |      if (index !in 0..<count) throw IndexOutOfBoundsException("index ${'$'}index out of bounds for array[${'$'}count]")
   |      return wrapElement(buffer, offset + index * stride, stride)
   |   }
   |
   |   val size: Int get() = count
   |}
""".trimMargin()
