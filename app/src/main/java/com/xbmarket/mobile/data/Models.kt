package com.xbmarket.mobile.data

import kotlinx.serialization.Serializable

@Serializable
data class GameSummary(
    val id: String,
    val title: String,
    val image: String? = null,
)

data class GameDetail(
    val id: String,
    val title: String,
    val publisher: String?,
    val description: String?,
    val heroImage: String?,
    val boxArt: String?,
)

/** Harga satu game di satu market. [price] = harga saat ini, [msrp] = harga normal. */
data class RegionPrice(
    val region: Region,
    val currency: String,
    val price: Double,
    val msrp: Double,
    val saleEnds: String?,
    /** Harga dalam mata uang dasar pengguna; null bila kurs tidak tersedia. */
    val converted: Double? = null,
) {
    val discountPercent: Int
        get() = if (msrp > 0 && price < msrp) ((1 - price / msrp) * 100).toInt() else 0
}

/** Hasil perbandingan harga: [prices] terurut dari termurah, [unavailable] market yang tidak menjual game ini. */
data class PriceComparison(
    val prices: List<RegionPrice>,
    val unavailable: List<Region>,
)
