package sbe4kt.ir

internal class CompressedFlags(
   val setName: String,
   val encodingType: String,
   val size: Int,
) {
   var offset: Int = -1
   var sbeOrder: Int = -1
   var fieldId: Int = -1
}
