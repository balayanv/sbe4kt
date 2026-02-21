package sbe4kt.kt

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import sbe4kt.KSP
import sbe4kt.util.trimTemplate

internal fun KSP.generateStr64ValueClassFile(charSetDeclaration: KSClassDeclaration) {
   val charSetName = charSetDeclaration.simpleName.asString()
   val className = "${charSetName}Str64"
   val packageName = charSetDeclaration.packageName.asString()

   val content = $$"""
      |@file:Suppress("unused")
      |
      |package $$packageName
      |
      |import sbe4kt.lib.CodecStr64
      |import sbe4kt.lib.CharArraySequence
      |import sbe4kt.lib.toBinaryString
      |
      |@JvmInline
      |value class $$className(val value: Long) : CharSequence {
      |   companion object {
      |      val codec = CodecStr64($$charSetName)
      |
      |      @JvmStatic
      |      fun encode(charSequence: CharSequence) = $$className(codec.encode(charSequence))
      |
      |      @JvmStatic
      |      fun from(charSequence: CharSequence) = encode(charSequence)
      |
      |      @JvmStatic
      |      fun randomStr() = encode(codec.randomStr())
      |   }
      |
      |   override val length: Int get() = codec.length(value)
      |
      |   override operator fun get(index: Int) = codec.getChar(value, index)
      |
      |   override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
      |      val len = endIndex - startIndex
      |      val char = CharArraySequence(CharArray(len))
      |      for (i in startIndex until endIndex) {
      |         char.array[i] = codec.getChar(value, i)
      |      }
      |      return char
      |   }
      |
      |   fun decode(appendable: Appendable) = codec.decode(value, appendable)
      |
      |   fun decode() = codec.decode(value)
      |
      |   override fun toString() = decode().toString()
      |   fun toDebugString() = "${toString()} (${toString().length} characters) $codec\n[$value]"
      |   fun toDebugBinaryString() = "${toString()} (${toString().length} characters) $codec\n[${value.toBinaryString(chunkSize = codec.charset.bitWidth)}]"
      |}
      |""".trimTemplate()

   val file = gen.createNewFile(
      Dependencies(true, *charSetDeclaration.containingFile?.let { arrayOf(it) } ?: emptyArray()),
      packageName,
      className
   )

   file.bufferedWriter().use { it.write(content) }
}
