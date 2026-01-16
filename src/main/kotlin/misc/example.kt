package utils


import sbe4kt.CharSet64
import kotlin.jvm.JvmInline
import sbe4kt.CodecStr64

// define your custom charset
object ANU : CharSet64("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_")

@JvmInline
value class CompactString(val value: Long) : CharSequence {
   companion object {
      val codec = CodecStr64(ANU)

      @JvmStatic
      fun encode(charSequence: CharSequence): CompactString {
         return CompactString(codec.encode(charSequence))
      }

      @JvmStatic
      fun from(charSequence: CharSequence): CompactString {
         return encode(charSequence)
      }
   }

   override val length: Int
      get() = codec.length(value)

   override operator fun get(index: Int): Char {
      return codec.getChar(value, index)
   }

   override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
      return codec.decode(value).subSequence(startIndex, endIndex)
   }

   fun decode(appendable: Appendable) {
      codec.decode(value, appendable)
   }

   fun decode(): CharSequence {
      return codec.decode(value)
   }

   override fun toString(): String {
      return decode().toString()
   }
}


fun main() {
   val str : CompactString = CompactString.from("XBT_123123USD")
   println("${str.value} : $str")

}