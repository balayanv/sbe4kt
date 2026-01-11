package com.example.market

import com.example.sbe4kt.SBE

@SBE
data class MarketData(
    var id: Long,
    var price: Double,
    var volume: Int,
    var active: Boolean
)
