package com.xbmarket.mobile.data

import kotlinx.serialization.Serializable

/** Pilihan di filter Diskon. Platform dan genre dikirim ke server; sisanya disaring di aplikasi. */
@Serializable
data class DealFilters(
    val platforms: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val minDiscount: Int = 0,
    /** Batas harga dalam mata uang dasar; null = tanpa batas. */
    val maxPrice: Double? = null,
    val kind: Kind = Kind.Game,
    val hideGamePass: Boolean = false,
    val sort: Sort = Sort.Discount,
) {
    @Serializable
    enum class Kind(val label: String) { All("Semua"), Game("Game"), AddOn("Add-on") }

    /** Hanya [Discount] yang sesuai urutan server; lainnya mengurutkan item yang sudah dimuat. */
    @Serializable
    enum class Sort(val label: String) { Discount("Diskon terbesar"), PriceAsc("Harga termurah"), Newest("Terbaru") }

    /** Jumlah filter aktif selain urutan, untuk badge di tombol Filter. */
    val activeCount: Int
        get() = platforms.size + genres.size + (if (minDiscount > 0) 1 else 0) + (if (maxPrice != null) 1 else 0) +
            (if (kind != Kind.Game) 1 else 0) + (if (hideGamePass) 1 else 0)

    /** Bagian yang mengubah hasil dari server; perubahan di sini perlu memuat ulang. */
    fun serverKey() = Triple(platforms, genres, minDiscount)

    companion object {
        /** ID filter PlayWith dari xbox.com beserta label tampilan. */
        val Platforms = listOf(
            "XboxSeriesX|S" to "Xbox Series X|S",
            "XboxOne" to "Xbox One",
            "PC" to "PC",
            "Handheld" to "Handheld",
            "CloudGaming" to "Cloud Gaming",
            "XboxPlayAnywhere" to "Play Anywhere",
        )

        /** ID filter Genre dari xbox.com (dipakai juga sebagai label). */
        val Genres = listOf(
            "Action & adventure", "Card & board", "Casino", "Classics", "Educational", "Family & kids", "Fighting",
            "Multi-Player Online Battle Arena", "Music", "Platformer", "Puzzle & trivia", "Racing & flying",
            "Role playing", "Shooter", "Simulation", "Sports", "Strategy", "Word", "Other",
        )

        val Discounts = listOf(0, 25, 50, 75, 90)
    }
}
