package tests

import sbe4kt.SBE

@SBE(id = 2, usePadding = true) // Explicit ID
data class ExplicitIdMessage(
   var fieldA: Long,
   var fieldB: Int
)

@SBE
data class ImplicitIdMessage(
   var price: Double,
   var quantity: Int
)

@SBE
data class ImplicitIdMessage2(
   var price: Double,
   var quantity: Int
)

@SBE
data class ImplicitIdMessage4(
   var price: Double,
   var quantity: Int
)
