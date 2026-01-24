package sbe4kt.lib

@JvmInline
value class CharArraySequence(val array : CharArray) : CharSequence{
   override val length: Int get() = array.size

   override fun get(index: Int) = array[index]

   override fun subSequence(startIndex: Int, endIndex: Int) = CharArraySequence(array.copyOfRange(startIndex, endIndex))
}