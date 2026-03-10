package benchmark

import sbe4kt.SBE

enum class Status {
    NEW, ACTIVE, INACTIVE, DELETED
}

@SBE(isStruct = true)
data class Counters(
    var c0: Int,
    var c1: Int,
    var c2: Int,
    var c3: Int
)

@SBE(isStruct = true)
data class Coordinates(
    var latitude: Float,
    var longitude: Float
)

@SBE(isStruct = true)
data class DecimalPrice(
    var mantissa: Long,
    var exponent: Byte
)

@SBE(isStruct = true)
data class HiResTimestamp(
    var seconds: Long,
    var nanoseconds: Int
)

@SBE(isStruct = true)
data class Dimensions(
    var length: Short,
    var width: Short,
    var height: Short
)

@SBE(isStruct = true)
data class Identifier(
    var source: Byte,
    var code: Int,
    var region: Short
)

@SBE
data class FixedMessage(
    var fieldByte1: Byte,
    var fieldByte2: Byte,
    var fieldShort1: Short,
    var fieldShort2: Short,
    var fieldInt1: Int,
    var fieldInt2: Int,
    var fieldLong1: Long,
    var fieldLong2: Long,
    var fieldFloat: Float,
    var fieldDouble: Double,
    var fieldChar: Char,
    var fieldLong3: Long,
    var status: Status,
    var counters: Counters,
    var coordinates: Coordinates,
    var price: DecimalPrice,
    var timestamp: HiResTimestamp,
    var dimensions: Dimensions,
    var identifier: Identifier
)
