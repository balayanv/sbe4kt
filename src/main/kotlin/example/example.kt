package example

import sbe4kt.META
import sbe4kt.SBE


@SBE(usePadding = true)
data class Example(
   @META(size = 16)
   var f1: String,
)

fun main() {

   randomExample().printSbeLayout()

}